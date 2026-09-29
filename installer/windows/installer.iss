; Windows installer for POS System for Tavuzi Gadgets. Written by BS Codes.
;
; Built on a GitHub Windows machine by .github/workflows/windows-installer.yml
; (Inno Setup: https://jrsoftware.org/isinfo.php). It copies the app, its own
; Java and PostgreSQL into Program Files, then runs setup.ps1, which creates
; the database, the settings, the Windows services and the shortcuts.
; Upgrades: run a newer setup file; data and passwords are kept.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
; Never change AppId: Windows uses it to recognise upgrades of this app.
AppId=BSCodes-pos-system-for-tavuzi-gadgets
AppName=POS System for Tavuzi Gadgets
AppVersion={#AppVersion}
AppPublisher=BS Codes
DefaultDirName={autopf}\POS System for Tavuzi Gadgets
DisableProgramGroupPage=yes
DisableDirPage=auto
OutputDir=Output
OutputBaseFilename=pos-system-for-tavuzi-gadgets-setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
WizardStyle=modern
UninstallDisplayName=POS System for Tavuzi Gadgets
CloseApplications=no

[Files]
Source: "build\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "build\java\*"; DestDir: "{app}\java"; Flags: ignoreversion recursesubdirs
Source: "build\pgsql\*"; DestDir: "{app}\pgsql"; Flags: ignoreversion recursesubdirs
Source: "build\settings-example.env"; DestDir: "{app}"; Flags: ignoreversion
Source: "setup.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "backup.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "uninstall.ps1"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{commonprograms}\POS System for Tavuzi Gadgets backups"; Filename: "{commonappdata}\pos-system-for-tavuzi-gadgets\backups"

[Run]
Filename: "{commonappdata}\pos-system-for-tavuzi-gadgets\POS System for Tavuzi Gadgets.url"; Description: "Open POS System for Tavuzi Gadgets"; Flags: postinstall shellexec skipifsilent skipifdoesntexist nowait

[UninstallRun]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\uninstall.ps1"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoveServices"

[UninstallDelete]
Type: files; Name: "{app}\app\service.xml"

[Code]
function RunScript(const FileName: String): Integer;
var
  ResultCode: Integer;
begin
  Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'),
    '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\') + FileName + '"',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Result := ResultCode;
end;

// Upgrades: stop the running app and database so their files can be replaced.
// (On a first install the services don't exist yet and this does nothing.)
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Exec(ExpandConstant('{sys}\net.exe'), 'stop "pos-system-for-tavuzi-gadgets"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Exec(ExpandConstant('{sys}\net.exe'), 'stop "pos-system-for-tavuzi-gadgets-db"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Result := '';
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    WizardForm.StatusLabel.Caption := 'Setting up the database and starting POS System for Tavuzi Gadgets (about a minute)...';
    if RunScript('setup.ps1') <> 0 then
      SuppressibleMsgBox('POS System for Tavuzi Gadgets was installed but did not start. Please send this file to BS Codes:' + #13#10 +
        ExpandConstant('{commonappdata}\pos-system-for-tavuzi-gadgets\logs\setup.log'), mbError, MB_OK, IDOK);
  end;
end;
