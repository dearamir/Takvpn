#define MyAppName "TakVPN"
#define MyAppVersion "0.1.0"
#define MyAppPublisher "TakVPN"
#define MyAppExeName "TakVPN.exe"

[Setup]
AppId={{6EFA21A0-69DA-4A83-AC09-2E6A2D5D45A1}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\TakVPN
DefaultGroupName=TakVPN
DisableProgramGroupPage=yes
OutputDir=../..
OutputBaseFilename=TakVPN-Setup-x64
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "../TakVPN.Windows/bin/Release/net8.0-windows/win-x64/publish/*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\TakVPN"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\TakVPN"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch TakVPN"; Flags: postinstall nowait skipifsilent
