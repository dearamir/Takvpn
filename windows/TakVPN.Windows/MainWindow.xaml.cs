using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Net.Sockets;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;

namespace TakVPN.Windows;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<VpnProfile> _profiles = new();
    private readonly string _storePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TakVPN", "profiles.json");

    public MainWindow()
    {
        InitializeComponent();
        ProfilesList.ItemsSource = _profiles;
        LoadProfiles();
    }

    private void AddConfig_Click(object sender, RoutedEventArgs e)
    {
        var link = ConfigInput.Text.Trim();
        if (!VpnProfile.TryParse(link, out var profile, out var error))
        {
            MessageBox.Show(error, "TakVPN", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        if (_profiles.Any(p => p.Link == link)) { MessageBox.Show("این کانفیگ قبلاً اضافه شده است."); return; }
        _profiles.Add(profile!);
        ProfilesList.SelectedItem = profile;
        ConfigInput.Clear();
        SaveProfiles();
    }

    private void RemoveConfig_Click(object sender, RoutedEventArgs e)
    {
        if (ProfilesList.SelectedItem is not VpnProfile profile) return;
        _profiles.Remove(profile);
        SaveProfiles();
        ClearDetails();
    }

    private async void TestLatency_Click(object sender, RoutedEventArgs e)
    {
        if (ProfilesList.SelectedItem is not VpnProfile profile) return;
        StatusText.Text = "در حال بررسی دسترسی TCP…";
        var sw = Stopwatch.StartNew();
        try
        {
            using var client = new TcpClient();
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            await client.ConnectAsync(profile.Host, profile.Port, cts.Token);
            sw.Stop();
            profile.LatencyMs = (int)sw.ElapsedMilliseconds;
            DetailLatency.Text = $"{profile.LatencyMs} ms (TCP)";
            StatusText.Text = "سرور از طریق TCP در دسترس است";
        }
        catch (Exception ex)
        {
            profile.LatencyMs = null;
            DetailLatency.Text = "عدم دسترسی";
            StatusText.Text = "بررسی TCP ناموفق بود";
            Debug.WriteLine(ex);
        }
        RefreshSelected();
        SaveProfiles();
    }

    private void ProfilesList_SelectionChanged(object sender, SelectionChangedEventArgs e) => RefreshSelected();

    private void RefreshSelected()
    {
        if (ProfilesList.SelectedItem is not VpnProfile p) { ClearDetails(); return; }
        DetailName.Text = p.Name;
        DetailProtocol.Text = p.Protocol;
        DetailHost.Text = p.Host;
        DetailPort.Text = p.Port.ToString();
        DetailLatency.Text = p.LatencyMs is null ? "اندازه‌گیری نشده" : $"{p.LatencyMs} ms (TCP)";
        ProfilesList.Items.Refresh();
    }

    private void ClearDetails()
    {
        DetailName.Text = DetailProtocol.Text = DetailHost.Text = DetailPort.Text = "—";
        DetailLatency.Text = "اندازه‌گیری نشده";
    }

    private void LoadProfiles()
    {
        try
        {
            if (!File.Exists(_storePath)) return;
            var data = JsonSerializer.Deserialize<List<VpnProfile>>(File.ReadAllText(_storePath));
            if (data is not null) foreach (var p in data) _profiles.Add(p);
        }
        catch (Exception ex) { Debug.WriteLine(ex); }
    }

    private void SaveProfiles()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(_storePath)!);
            File.WriteAllText(_storePath, JsonSerializer.Serialize(_profiles));
        }
        catch (Exception ex) { MessageBox.Show($"ذخیره کانفیگ‌ها ناموفق بود: {ex.Message}", "TakVPN"); }
    }
}

public sealed class VpnProfile
{
    public string Name { get; set; } = "کانفیگ بدون نام";
    public string Protocol { get; set; } = "";
    public string Host { get; set; } = "";
    public int Port { get; set; }
    public string Link { get; set; } = "";
    public int? LatencyMs { get; set; }
    public string LatencyLabel => LatencyMs is null ? "—" : $"{LatencyMs} ms";

    public static bool TryParse(string link, out VpnProfile? profile, out string error)
    {
        profile = null;
        error = "فرمت لینک پشتیبانی نمی‌شود. فعلاً VLESS، VMess و Trojan قابل افزودن هستند.";
        try
        {
            var protocol = link[..Math.Max(0, link.IndexOf("://", StringComparison.Ordinal))].ToLowerInvariant();
            if (protocol is not ("vless" or "vmess" or "trojan")) return false;
            string host;
            int port = 443;
            string name = "";
            if (protocol == "vmess")
            {
                var payload = link["vmess://".Length..].Split('#', '?')[0].Replace('-', '+').Replace('_', '/');
                payload += new string('=', (4 - payload.Length % 4) % 4);
                using var doc = JsonDocument.Parse(System.Text.Encoding.UTF8.GetString(Convert.FromBase64String(payload)));
                var root = doc.RootElement;
                host = root.GetProperty("add").GetString() ?? "";
                if (root.TryGetProperty("port", out var p)) int.TryParse(p.ToString(), out port);
                if (root.TryGetProperty("ps", out var ps)) name = ps.GetString() ?? "";
            }
            else
            {
                var uri = new Uri(link);
                host = uri.Host;
                if (!uri.IsDefaultPort) port = uri.Port;
                name = Uri.UnescapeDataString(uri.Fragment.TrimStart('#'));
            }
            if (string.IsNullOrWhiteSpace(host) || port is < 1 or > 65535) return false;
            profile = new VpnProfile { Name = string.IsNullOrWhiteSpace(name) ? host : name, Protocol = protocol.ToUpperInvariant(), Host = host, Port = port, Link = link };
            error = "";
            return true;
        }
        catch (Exception ex) { error = $"لینک معتبر نیست: {ex.Message}"; return false; }
    }
}