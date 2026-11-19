# TakVPN for Windows

Windows 10/11 x64 desktop client. This is an isolated workstream and does not modify the Android app.

## Current implementation
- Persian RTL WPF desktop interface
- Import and local storage for VLESS, VMess and Trojan share links
- Xray-core process integration with the official Windows x64 binary
- Wintun TUN inbound, automatic system routes and outbound interface binding
- DNS and misconfigured-tunnel leak protection options
- Real proxy latency test: temporary local SOCKS inbound plus HTTPS request through Xray (not a raw TCP port check)
- Connect/disconnect lifecycle and cleanup on app close
- Windows GitHub Actions build that downloads the official Xray release, verifies its SHA-256 when supplied by GitHub, bundles Xray + Wintun, and publishes a ZIP preview

## Important limitations
This is an early integration and has **not yet been validated on a physical Windows machine**. VPN routing, UAC elevation, IPv4/IPv6 behavior, Wintun adapter creation/removal, captive portals, sleep/resume, and every supported transport must be tested before calling it production-ready. The app manifest requests administrator approval because Xray configures system routes and DNS.

The initial parser supports VLESS, VMess and Trojan. It does not yet support every protocol/URI variant accepted by the Android app (for example Hysteria2 and Shadowsocks).

## Build
Requires .NET 8 SDK on Windows. The CI workflow downloads the official Xray Windows x64 ZIP and bundles its `xray.exe` and `wintun.dll` beside the published app.

```powershell
dotnet publish .\TakVPN.Windows.csproj -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```

The CI artifact is a ZIP preview. It is not an MSI installer yet.
