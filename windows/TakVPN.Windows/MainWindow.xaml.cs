using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;

namespace TakVPN.Windows;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<VpnProfile> _profiles = new();
    private readonly string _storePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TakVPN", "profiles.json");
    private readonly XrayEngine _engine = new();
    private bool _busy;

    public MainWindow()
    {
        InitializeComponent();
        ProfilesList.ItemsSource = _profiles;
        LoadProfiles();
        UpdateButtons();
    }

    private void AddConfig_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var link = ConfigInput.Text.Trim();
            if (string.IsNullOrWhiteSpace(link))
            {
                MessageBox.Show("ابتدا لینک کانفیگ VLESS را وارد کنید.", "TakVPN", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            if (!VpnProfile.TryParse(link, out var profile, out var error) || profile is null)
            {
                MessageBox.Show(error, "TakVPN", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            if (_profiles.Any(p => string.Equals(p.Link, link, StringComparison.Ordinal)))
            {
                MessageBox.Show("این کانفیگ قبلاً اضافه شده است.", "TakVPN", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            _profiles.Add(profile);
            ProfilesList.SelectedItem = profile;
            ConfigInput.Clear();
            SaveProfiles();
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Add VLESS config failed: {ex}");
            MessageBox.Show(
                $"افزودن کانفیگ انجام نشد؛ برنامه نباید بسته شود.\n{ex.Message}",
                "TakVPN - خطای افزودن کانفیگ",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
    }

    private void RemoveConfig_Click(object sender, RoutedEventArgs e)
    {
        if (ProfilesList.SelectedItem is not VpnProfile profile) return;
        if (_engine.IsRunning) { MessageBox.Show("ابتدا VPN را قطع کنید."); return; }
        _profiles.Remove(profile);
        SaveProfiles();
        ClearDetails();
    }

    private async void TestLatency_Click(object sender, RoutedEventArgs e)
    {
        if (ProfilesList.SelectedItem is not VpnProfile profile || _busy) return;
        SetBusy(true, "در حال راه‌اندازی هسته و سنجش تأخیر واقعی…");
        try
        {
            profile.LatencyMs = await _engine.MeasureProxyLatencyAsync(profile);
            DetailLatency.Text = $"{profile.LatencyMs} ms";
            StatusText.Text = "تست تونل موفق بود";
            StatusHint.Text = "زمان درخواست HTTPS از مسیر Xray اندازه‌گیری شد.";
        }
        catch (Exception ex)
        {
            profile.LatencyMs = null;
            DetailLatency.Text = "ناموفق";
            StatusText.Text = "تست کانفیگ انجام نشد";
            StatusHint.Text = ex.Message;
            Debug.WriteLine(ex);
            MessageBox.Show(ex.Message, "TakVPN - تست کانفیگ", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally { RefreshSelected(); SaveProfiles(); SetBusy(false); }
    }

    private async void TestAll_Click(object sender, RoutedEventArgs e)
    {
        if (_busy || _engine.IsRunning || _profiles.Count == 0) return;
        SetBusy(true, "آغاز تست گروهی؛ هر کانفیگ جداگانه از مسیر Xray بررسی می‌شود…");
        var passed = 0;
        try
        {
            foreach (var profile in _profiles)
            {
                StatusText.Text = $"در حال تست: {profile.Name}";
                StatusHint.Text = "در حال برقراری اتصال آزمایشی امن…";
                try
                {
                    profile.LatencyMs = await _engine.MeasureProxyLatencyAsync(profile);
                    passed++;
                }
                catch (Exception ex)
                {
                    profile.LatencyMs = null;
                    Debug.WriteLine($"Group test failed for {profile.Name}: {ex}");
                }
                ProfilesList.Items.Refresh();
                if (ProfilesList.SelectedItem == profile) RefreshSelected();
            }
            StatusText.Text = $"تست گروهی تمام شد: {passed} از {_profiles.Count} موفق";
            StatusHint.Text = "مقادیر، زمان درخواست HTTPS از مسیر Xray هستند؛ نه پینگ ICMP.";
            SaveProfiles();
        }
        finally { SetBusy(false); UpdateButtons(); }
    }

    private async void Connect_Click(object sender, RoutedEventArgs e)
    {
        if (ProfilesList.SelectedItem is not VpnProfile profile || _busy) return;
        SetBusy(true, "در حال ایجاد تونل امن…");
        try
        {
            await _engine.StartAsync(profile);
            StatusText.Text = "متصل";
            StatusHint.Text = $"از طریق {profile.Name} و Xray/Wintun";
        }
        catch (Exception ex)
        {
            StatusText.Text = "اتصال برقرار نشد";
            StatusHint.Text = ex.Message;
            MessageBox.Show(ex.Message, "TakVPN - اتصال", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally { SetBusy(false); UpdateButtons(); }
    }

    private async void Disconnect_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        SetBusy(true, "در حال قطع اتصال و بازگرداندن مسیرها…");
        try
        {
            await _engine.StopAsync();
            StatusText.Text = "قطع شده";
            StatusHint.Text = "تونل VPN بسته شد.";
        }
        catch (Exception ex) { MessageBox.Show(ex.Message, "TakVPN"); }
        finally { SetBusy(false); UpdateButtons(); }
    }

    private void SetBusy(bool busy, string? hint = null)
    {
        _busy = busy;
        if (hint is not null) StatusHint.Text = hint;
        ConnectButton.IsEnabled = !busy && !_engine.IsRunning && ProfilesList.SelectedItem is VpnProfile;
        DisconnectButton.IsEnabled = !busy && _engine.IsRunning;
        TestButton.IsEnabled = !busy && !_engine.IsRunning && ProfilesList.SelectedItem is VpnProfile;
        TestAllButton.IsEnabled = !busy && !_engine.IsRunning && _profiles.Count > 0;
        ProfilesList.IsEnabled = !busy && !_engine.IsRunning;
    }

    private void UpdateButtons() => SetBusy(false);

    private void ProfilesList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        RefreshSelected();
        UpdateButtons();
    }

    private void RefreshSelected()
    {
        if (ProfilesList.SelectedItem is not VpnProfile p) { ClearDetails(); return; }
        DetailName.Text = p.Name;
        DetailProtocol.Text = p.Protocol;
        DetailHost.Text = p.Host;
        DetailPort.Text = p.Port.ToString();
        DetailLatency.Text = p.LatencyMs is null ? "اندازه‌گیری نشده" : $"{p.LatencyMs} ms";
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

    protected override async void OnClosed(EventArgs e)
    {
        try { await _engine.StopAsync(); } catch (Exception ex) { Debug.WriteLine(ex); }
        _engine.Dispose();
        base.OnClosed(e);
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
    public string ProtocolDisplay => Protocol.ToUpperInvariant();
    public string CompactLine => (LatencyMs is null ? "—" : $"{LatencyMs} ms") + "   " + Host + ":" + Port;

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
                using var doc = JsonDocument.Parse(System.Convert.FromBase64String(payload));
                var root = doc.RootElement;
                host = root.GetProperty("add").GetString() ?? "";
                if (root.TryGetProperty("port", out var p) && int.TryParse(p.ToString(), out var parsedPort)) port = parsedPort;
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