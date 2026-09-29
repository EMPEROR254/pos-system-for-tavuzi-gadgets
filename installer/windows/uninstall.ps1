# Removes the POS System for Tavuzi Gadgets services, shortcuts, firewall rule and backup task.
# Written by BS Codes; run by the uninstaller before it deletes the program files.
#
# The data in C:\ProgramData\pos-system-for-tavuzi-gadgets (database, backups, settings) is KEPT on
# purpose: uninstalling must never lose a client's records. Reinstalling picks
# it up again. Delete that folder by hand only when the data is truly not needed.
$ErrorActionPreference = 'Continue'

$Slug = 'pos-system-for-tavuzi-gadgets'
$AppName = 'POS System for Tavuzi Gadgets'
$DataDir = Join-Path $env:ProgramData $Slug
$DbService = "$Slug-db"

if (Get-Service -Name $Slug -ErrorAction SilentlyContinue) {
  Stop-Service -Name $Slug -Force -ErrorAction SilentlyContinue
  & (Join-Path $PSScriptRoot 'app\service.exe') uninstall
}
if (Get-Service -Name $DbService -ErrorAction SilentlyContinue) {
  Stop-Service -Name $DbService -Force -ErrorAction SilentlyContinue
  & (Join-Path $PSScriptRoot 'pgsql\bin\pg_ctl.exe') unregister -N $DbService
}

Get-NetFirewallRule -ErrorAction SilentlyContinue |
  Where-Object { $_.DisplayName -like "$AppName (port *)" } | Remove-NetFirewallRule
Unregister-ScheduledTask -TaskName "$AppName backup" -Confirm:$false -ErrorAction SilentlyContinue

$desktop = [Environment]::GetFolderPath('CommonDesktopDirectory')
$startMenu = [Environment]::GetFolderPath('CommonPrograms')
foreach ($folder in @($desktop, $startMenu, $DataDir)) {
  Remove-Item -Path (Join-Path $folder "$AppName.url") -Force -ErrorAction SilentlyContinue
}
Write-Output "$AppName removed. Data kept in $DataDir"
