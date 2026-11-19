# TakVPN for Windows

This isolated workstream does not modify the Android application.

## Included in this preview
- Persian RTL WPF interface for Windows 10/11
- Import and local storage for VLESS, VMess and Trojan share links
- Protocol, host and port details
- TCP reachability timing (not ICMP ping, not VPN handshake latency)
- Local profile storage in %LOCALAPPDATA%\\TakVPN

## Not implemented
This preview does **not** establish a VPN tunnel. The connect control remains disabled until a Windows-compatible Xray core, TUN adapter lifecycle, routing/DNS handling, privilege management and safe disconnect cleanup are integrated and tested.

## Build
Requires .NET 8 SDK on Windows:
```powershell
dotnet publish .\\TakVPN.Windows\\TakVPN.Windows.csproj -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```
