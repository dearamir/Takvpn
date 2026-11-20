using System.IO;
using System.Net.Http;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Net.Security;
using System.Security.Authentication;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Collections.Concurrent;
using System.Web;

namespace TakVPN.Windows;

public sealed class XrayEngine : IDisposable
{
    private Process? _process;
    private readonly string _dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TakVPN");
    private string CorePath => Path.Combine(AppContext.BaseDirectory, "tools", "xray", "xray.exe");

    public bool IsRunning => _process is { HasExited: false };

    public async Task StartAsync(VpnProfile profile)
    {
        if (IsRunning) throw new InvalidOperationException("VPN is already running.");
        EnsureCore();
        Directory.CreateDirectory(_dataDir);
        var configPath = Path.Combine(_dataDir, "active-config.json");
        await File.WriteAllTextAsync(configPath, BuildConfig(profile, true).ToJsonString(new JsonSerializerOptions { WriteIndented = true }), new UTF8Encoding(false));
        await ValidateConfigAsync(configPath);
        var start = new ProcessStartInfo(CorePath)
        {
            WorkingDirectory = Path.GetDirectoryName(CorePath)!,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };
        start.ArgumentList.Add("run");
        start.ArgumentList.Add("-c");
        start.ArgumentList.Add(configPath);
        var startupLines = new ConcurrentQueue<string>();
        void Capture(string? line)
        {
            if (string.IsNullOrWhiteSpace(line)) return;
            startupLines.Enqueue(line);
            Debug.WriteLine("[Xray] " + line);
            while (startupLines.Count > 100 && startupLines.TryDequeue(out _)) { }
            try
            {
                File.AppendAllText(Path.Combine(_dataDir, "xray-startup.log"),
                    $"[{DateTimeOffset.Now:O}] {line}{Environment.NewLine}", new UTF8Encoding(false));
            }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }

        _process = new Process { StartInfo = start, EnableRaisingEvents = true };
        _process.OutputDataReceived += (_, e) => Capture(e.Data);
        _process.ErrorDataReceived += (_, e) => Capture(e.Data);
        if (!_process.Start()) throw new InvalidOperationException("Xray process could not start.");
        _process.BeginOutputReadLine();
        _process.BeginErrorReadLine();
        await Task.Delay(1800);
        if (_process.HasExited)
        {
            var code = _process.ExitCode;
            var details = string.Join(Environment.NewLine, startupLines);
            if (string.IsNullOrWhiteSpace(details))
                details = "Xray closed without writing diagnostic output. Check the bundled Xray/Wintun files and run TakVPN as Administrator.";
            _process.Dispose();
            _process = null;
            throw new InvalidOperationException(
                $"Xray exited during startup (code {code}).{Environment.NewLine}{Environment.NewLine}" +
                $"{details}{Environment.NewLine}{Environment.NewLine}" +
                $"Full log: {Path.Combine(_dataDir, "xray-startup.log")}");
        }
    }

    public async Task StopAsync()
    {
        var p = _process;
        _process = null;
        if (p is null) return;
        try
        {
            if (!p.HasExited)
            {
                p.Kill(entireProcessTree: true);
                await p.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(8));
            }
        }
        catch (InvalidOperationException) { }
        catch (TimeoutException) { }
        finally { p.Dispose(); }
    }

    public async Task<int> MeasureProxyLatencyAsync(VpnProfile profile, CancellationToken cancellationToken = default)
    {
        EnsureCore();
        var port = GetFreePort();
        var temp = Path.Combine(Path.GetTempPath(), "takvpn-test-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(temp);
        var configPath = Path.Combine(temp, "config.json");
        var config = BuildConfig(profile, false);
        config["inbounds"] = new JsonArray(new JsonObject
        {
            ["tag"] = "socks-in",
            ["listen"] = "127.0.0.1",
            ["port"] = port,
            ["protocol"] = "socks",
            ["settings"] = new JsonObject { ["auth"] = "noauth", ["udp"] = true }
        });
        await File.WriteAllTextAsync(configPath, config.ToJsonString(), cancellationToken);
        var start = new ProcessStartInfo(CorePath)
        {
            WorkingDirectory = Path.GetDirectoryName(CorePath)!,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };
        start.ArgumentList.Add("run"); start.ArgumentList.Add("-c"); start.ArgumentList.Add(configPath);
        using var process = new Process { StartInfo = start };
        try
        {
            if (!process.Start()) throw new InvalidOperationException("Could not start Xray for latency test.");
            process.BeginOutputReadLine(); process.BeginErrorReadLine();
            await WaitForPortAsync(port, process, cancellationToken);
            var sw = Stopwatch.StartNew();
            var status = await ProbeHttpsThroughSocksAsync(port, "www.gstatic.com", "/generate_204", cancellationToken);
            sw.Stop();
            if (status is not (200 or 204)) throw new HttpRequestException($"Latency endpoint returned HTTP {status}.");
            return (int)sw.ElapsedMilliseconds;
        }
        finally
        {
            try { if (!process.HasExited) process.Kill(entireProcessTree: true); } catch { }
            try { await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5), cancellationToken); } catch { }
            try { Directory.Delete(temp, true); } catch { }
        }
    }

    private static async Task<int> ProbeHttpsThroughSocksAsync(int proxyPort, string host, string path, CancellationToken ct)
    {
        using var tcp = new TcpClient();
        await tcp.ConnectAsync(IPAddress.Loopback, proxyPort, ct);
        await using var network = tcp.GetStream();

        await network.WriteAsync(new byte[] { 5, 1, 0 }, ct);
        var greeting = new byte[2];
        await network.ReadExactlyAsync(greeting, ct);
        if (greeting[0] != 5 || greeting[1] != 0) throw new IOException("Local SOCKS5 proxy rejected no-auth mode.");

        var hostBytes = Encoding.ASCII.GetBytes(host);
        var connect = new byte[7 + hostBytes.Length];
        connect[0] = 5; connect[1] = 1; connect[2] = 0; connect[3] = 3; connect[4] = (byte)hostBytes.Length;
        Buffer.BlockCopy(hostBytes, 0, connect, 5, hostBytes.Length);
        connect[^2] = 1; connect[^1] = 187; // TCP 443
        await network.WriteAsync(connect, ct);

        var replyHead = new byte[4];
        await network.ReadExactlyAsync(replyHead, ct);
        if (replyHead[0] != 5 || replyHead[1] != 0) throw new IOException($"SOCKS5 CONNECT failed (code {replyHead[1]}).");
        var addressLength = replyHead[3] switch
        {
            1 => 4,
            4 => 16,
            3 => (await ReadOneAsync(network, ct))[0],
            _ => throw new IOException("Invalid SOCKS5 address type.")
        };
        var tail = new byte[addressLength + 2];
        await network.ReadExactlyAsync(tail, ct);

        await using var tls = new SslStream(network, leaveInnerStreamOpen: true);
        await tls.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
        {
            TargetHost = host,
            EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13
        }, ct);
        var request = Encoding.ASCII.GetBytes($"GET {path} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\nUser-Agent: TakVPN-Windows\r\n\r\n");
        await tls.WriteAsync(request, ct);
        using var response = new MemoryStream();
        var buffer = new byte[1024];
        while (response.Length < 8192)
        {
            var count = await tls.ReadAsync(buffer, ct);
            if (count == 0) break;
            response.Write(buffer, 0, count);
            var data = Encoding.ASCII.GetString(response.GetBuffer(), 0, (int)response.Length);
            var end = data.IndexOf("\r\n", StringComparison.Ordinal);
            if (end >= 0)
            {
                var firstLine = data[..end].Split(' ');
                if (firstLine.Length >= 2 && int.TryParse(firstLine[1], out var code)) return code;
                throw new IOException("Invalid HTTP response from latency endpoint.");
            }
        }
        throw new IOException("No HTTP response received through the Xray proxy.");
    }

    private static async Task<byte[]> ReadOneAsync(Stream stream, CancellationToken ct)
    {
        var value = new byte[1];
        await stream.ReadExactlyAsync(value, ct);
        return value;
    }

    private static async Task WaitForPortAsync(int port, Process process, CancellationToken ct)
    {
        var until = DateTime.UtcNow.AddSeconds(10);
        while (DateTime.UtcNow < until)
        {
            ct.ThrowIfCancellationRequested();
            if (process.HasExited) throw new InvalidOperationException("Xray exited before the local proxy became ready.");
            try { using var tcp = new TcpClient(); await tcp.ConnectAsync(IPAddress.Loopback, port, ct); return; }
            catch (SocketException) { await Task.Delay(150, ct); }
        }
        throw new TimeoutException("Local Xray proxy did not become ready.");
    }

    private async Task ValidateConfigAsync(string configPath)
    {
        var psi = new ProcessStartInfo(CorePath)
        {
            WorkingDirectory = Path.GetDirectoryName(CorePath)!,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };
        psi.ArgumentList.Add("run"); psi.ArgumentList.Add("-test"); psi.ArgumentList.Add("-c"); psi.ArgumentList.Add(configPath);
        using var p = Process.Start(psi) ?? throw new InvalidOperationException("Could not validate Xray config.");
        var stdout = p.StandardOutput.ReadToEndAsync();
        var stderr = p.StandardError.ReadToEndAsync();
        await p.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15));
        var details = (await stderr) + (await stdout);
        if (p.ExitCode != 0) throw new InvalidOperationException("Xray config validation failed: " + details);
    }

    private void EnsureCore()
    {
        if (!File.Exists(CorePath)) throw new FileNotFoundException("Xray Windows core is missing. Rebuild the Windows package so the official Xray core and Wintun driver are bundled.", CorePath);
        if (!File.Exists(Path.Combine(Path.GetDirectoryName(CorePath)!, "wintun.dll")))
            throw new FileNotFoundException("Wintun driver library is missing beside xray.exe.");
    }

    private static int GetFreePort()
    {
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        return ((IPEndPoint)listener.LocalEndpoint).Port;
    }

    private static JsonObject BuildConfig(VpnProfile profile, bool tun)
    {
        var outbound = ParseOutbound(profile.Link);
        var config = new JsonObject
        {
            ["log"] = new JsonObject { ["loglevel"] = "warning" },
            ["dns"] = new JsonObject { ["servers"] = new JsonArray("1.1.1.1", "8.8.8.8") },
            ["outbounds"] = new JsonArray(outbound,
                new JsonObject { ["tag"] = "direct", ["protocol"] = "freedom" },
                new JsonObject { ["tag"] = "block", ["protocol"] = "blackhole" })
        };
        if (tun)
        {
            config["inbounds"] = new JsonArray(new JsonObject
            {
                ["tag"] = "tun-in",
                ["port"] = 0,
                ["protocol"] = "tun",
                ["settings"] = new JsonObject
                {
                    ["name"] = "TakVPN",
                    ["desc"] = "TakVPN",
                    ["mtu"] = 1500,
                    ["gateway"] = new JsonArray("172.19.0.1/30", "fdfe:dcba:9876::1/126"),
                    ["dns"] = new JsonArray("1.1.1.1", "8.8.8.8"),
                    ["autoSystemRoutingTable"] = new JsonArray("0.0.0.0/0", "::/0"),
                    ["autoOutboundsInterface"] = "auto",
                    ["autoSystemWfpBlockLeak"] = new JsonArray("dns", "misconfigtun")
                }
            });
            config["routing"] = new JsonObject
            {
                ["domainStrategy"] = "IPIfNonMatch",
                ["rules"] = new JsonArray(
                    new JsonObject { ["type"] = "field", ["ip"] = new JsonArray("geoip:private"), ["outboundTag"] = "direct" },
                    new JsonObject { ["type"] = "field", ["inboundTag"] = new JsonArray("tun-in"), ["outboundTag"] = "proxy" })
            };
        }
        return config;
    }

    private static JsonObject ParseOutbound(string link)
    {
        if (link.StartsWith("vmess://", StringComparison.OrdinalIgnoreCase))
        {
            var payload = link["vmess://".Length..].Split('#', '?')[0].Replace('-', '+').Replace('_', '/');
            payload += new string('=', (4 - payload.Length % 4) % 4);
            using var doc = JsonDocument.Parse(Convert.FromBase64String(payload));
            var j = doc.RootElement;
            var host = j.GetProperty("add").GetString() ?? throw new FormatException("VMess host missing.");
            var port = ReadInt(j, "port", 443);
            var id = j.GetProperty("id").GetString() ?? throw new FormatException("VMess UUID missing.");
            var user = new JsonObject { ["id"] = id, ["alterId"] = ReadInt(j, "aid", 0), ["security"] = ReadString(j, "scy", "auto") };
            var settings = new JsonObject { ["vnext"] = new JsonArray(new JsonObject { ["address"] = host, ["port"] = port, ["users"] = new JsonArray(user) }) };
            var q = new Dictionary<string,string> {
                ["type"] = ReadString(j,"net","tcp"), ["host"] = ReadString(j,"host",""),
                ["path"] = ReadString(j,"path",""), ["security"] = ReadString(j,"tls","") == "tls" ? "tls" : "none",
                ["sni"] = ReadString(j,"sni",""), ["alpn"] = ReadString(j,"alpn",""), ["fp"] = ReadString(j,"fp",""),
                ["headerType"] = ReadString(j,"type",""), ["serviceName"] = ReadString(j,"path","")
            };
            return MakeOutbound("vmess", settings, BuildStream(q, host));
        }

        if (!Uri.TryCreate(link, UriKind.Absolute, out var uri)) throw new FormatException("Invalid share link.");
        var scheme = uri.Scheme.ToLowerInvariant();
        if (scheme is not ("vless" or "trojan")) throw new NotSupportedException("Windows build currently supports VLESS, VMess and Trojan links.");
        var hostName = uri.Host;
        var portNumber = uri.IsDefaultPort ? 443 : uri.Port;
        var query = HttpUtility.ParseQueryString(uri.Query);
        var qmap = query.AllKeys.Where(k => k is not null).ToDictionary(k => k!, k => query[k!] ?? "", StringComparer.OrdinalIgnoreCase);
        JsonObject serverSettings;
        if (scheme == "vless")
        {
            var user = new JsonObject { ["id"] = Uri.UnescapeDataString(uri.UserInfo), ["encryption"] = qmap.GetValueOrDefault("encryption", "none") };
            if (!string.IsNullOrWhiteSpace(qmap.GetValueOrDefault("flow"))) user["flow"] = qmap["flow"];
            serverSettings = new JsonObject { ["vnext"] = new JsonArray(new JsonObject { ["address"] = hostName, ["port"] = portNumber, ["users"] = new JsonArray(user) }) };
        }
        else
        {
            qmap.TryAdd("security", "tls");
            serverSettings = new JsonObject { ["servers"] = new JsonArray(new JsonObject { ["address"] = hostName, ["port"] = portNumber, ["password"] = Uri.UnescapeDataString(uri.UserInfo) }) };
        }
        return MakeOutbound(scheme, serverSettings, BuildStream(qmap, hostName));
    }

    private static JsonObject MakeOutbound(string protocol, JsonObject settings, JsonObject stream) =>
        new() { ["tag"] = "proxy", ["protocol"] = protocol, ["settings"] = settings, ["streamSettings"] = stream };

    private static JsonObject BuildStream(Dictionary<string,string> q, string address)
    {
        string Get(string key, string fallback = "") => q.TryGetValue(key, out var v) ? v : fallback;
        var network = Get("type", "tcp");
        if (network == "splithttp") network = "xhttp";
        var host = Get("host");
        var path = Get("path", "/");
        var stream = new JsonObject { ["network"] = network };
        switch (network)
        {
            case "ws":
                var ws = new JsonObject { ["path"] = path };
                if (host.Length > 0) ws["headers"] = new JsonObject { ["Host"] = host };
                stream["wsSettings"] = ws; break;
            case "grpc":
                stream["grpcSettings"] = new JsonObject { ["serviceName"] = Get("serviceName", path.TrimStart('/')) }; break;
            case "httpupgrade":
                stream["httpupgradeSettings"] = new JsonObject { ["path"] = path, ["host"] = host }; break;
            case "xhttp":
                stream["xhttpSettings"] = new JsonObject { ["path"] = path, ["host"] = host, ["mode"] = Get("mode", "auto") }; break;
            case "tcp":
            case "raw":
                if (Get("headerType") == "http")
                {
                    var headers = new JsonObject { ["Host"] = new JsonArray((host.Length > 0 ? host : address).Split(',').Select(x => (JsonNode?)JsonValue.Create(x.Trim())).ToArray()) };
                    var request = new JsonObject { ["version"] = "1.1", ["method"] = "GET", ["path"] = new JsonArray(path), ["headers"] = headers };
                    stream["tcpSettings"] = new JsonObject { ["header"] = new JsonObject { ["type"] = "http", ["request"] = request } };
                }
                break;
        }
        var security = Get("security", "none");
        stream["security"] = security;
        if (security == "tls")
        {
            var tls = new JsonObject { ["serverName"] = Get("sni", host.Length > 0 ? host : address), ["fingerprint"] = Get("fp", "chrome") };
            if (Get("alpn").Length > 0) tls["alpn"] = new JsonArray(Get("alpn").Split(',').Select(x => (JsonNode?)JsonValue.Create(x)).ToArray());
            if (Get("allowInsecure") is "1" or "true" || Get("insecure") is "1" or "true") tls["allowInsecure"] = true;
            stream["tlsSettings"] = tls;
        }
        else if (security == "reality")
        {
            stream["realitySettings"] = new JsonObject
            {
                ["serverName"] = Get("sni"), ["fingerprint"] = Get("fp", "chrome"),
                ["publicKey"] = Get("pbk"), ["shortId"] = Get("sid"), ["spiderX"] = Get("spx")
            };
        }
        return stream;
    }

    private static int ReadInt(JsonElement e, string key, int fallback) =>
        e.TryGetProperty(key, out var v) && int.TryParse(v.ToString(), out var n) ? n : fallback;
    private static string ReadString(JsonElement e, string key, string fallback) =>
        e.TryGetProperty(key, out var v) ? v.ToString() : fallback;

    public void Dispose() { StopAsync().GetAwaiter().GetResult(); }
}
