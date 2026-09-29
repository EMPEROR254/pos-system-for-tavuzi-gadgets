# Nightly database backup for POS System for Tavuzi Gadgets. Written by BS Codes.
#
# Run by the Windows scheduled task "POS System for Tavuzi Gadgets backup" (21:00 every day, or
# as soon as the PC is on after that). Saves one file per night in
# C:\ProgramData\pos-system-for-tavuzi-gadgets\backups and keeps the newest 14.
# Restore a backup with pg_restore (see installer/windows/README.md in the project).
$ErrorActionPreference = 'Stop'

$Slug = 'pos-system-for-tavuzi-gadgets'
$DataDir = Join-Path $env:ProgramData $Slug
$Backups = Join-Path $DataDir 'backups'
$Logs = Join-Path $DataDir 'logs'
$PgBin = Join-Path $PSScriptRoot 'pgsql\bin'
$KeepNewest = 14

New-Item -ItemType Directory -Force -Path $Backups, $Logs | Out-Null
Start-Transcript -Path (Join-Path $Logs 'backup.log') -Append | Out-Null

$state = Get-Content -Path (Join-Path $DataDir 'config\install.json') -Raw | ConvertFrom-Json
$file = Join-Path $Backups ("$($state.dbName)-" + (Get-Date -Format 'yyyy-MM-dd_HHmm') + '.dump')

$env:PGPASSWORD = $state.dbPassword
try {
  & (Join-Path $PgBin 'pg_dump.exe') -h localhost -p $state.dbPort -U $state.dbUser -Fc -f $file $state.dbName
  if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit code $LASTEXITCODE)." }
} finally {
  Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
Write-Output "Backup saved: $file"

Get-ChildItem -Path $Backups -Filter '*.dump' | Sort-Object LastWriteTime -Descending |
  Select-Object -Skip $KeepNewest | Remove-Item -Force
Stop-Transcript | Out-Null
