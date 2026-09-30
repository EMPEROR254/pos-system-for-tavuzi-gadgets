# Sets up POS System for Tavuzi Gadgets on a Windows PC. Written by BS Codes.
#
# The installer (installer.iss) copies the files, then runs this script as
# administrator. It is safe to run again (upgrades): the database, passwords
# and settings that already exist are kept.
#
#   C:\Program Files\POS System for Tavuzi Gadgets\   app\ (app.jar + service runner), java\, pgsql\
#                                   (replaced on every upgrade)
#   C:\ProgramData\pos-system-for-tavuzi-gadgets\        config\  install.json (ports, passwords) and
#                                            application.properties (settings)
#                                   pgdata\  the PostgreSQL database files
#                                   backups\ nightly database backups
#                                   logs\    setup, app and backup logs
#
# Windows services: "pos-system-for-tavuzi-gadgets-db" (PostgreSQL) and "pos-system-for-tavuzi-gadgets" (the app). Both
# start with the PC; the app restarts by itself if it crashes.
$ErrorActionPreference = 'Stop'

$Slug = 'pos-system-for-tavuzi-gadgets'
$AppName = 'POS System for Tavuzi Gadgets'
$DbName = 'pos_system_for_tavuzi_gadgets'

$InstallDir = $PSScriptRoot
$DataDir = Join-Path $env:ProgramData $Slug
$ConfigDir = Join-Path $DataDir 'config'
$PgData = Join-Path $DataDir 'pgdata'
$Backups = Join-Path $DataDir 'backups'
$Logs = Join-Path $DataDir 'logs'
$PgBin = Join-Path $InstallDir 'pgsql\bin'
$DbService = "$Slug-db"

foreach ($dir in @($DataDir, $ConfigDir, $Backups, $Logs)) {
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
Start-Transcript -Path (Join-Path $Logs 'setup.log') -Append | Out-Null

# Random letters and digits (no symbols, so they are safe in any config file).
function New-Secret([int]$Length = 32) {
  $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
  $bytes = New-Object byte[] $Length
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $text = ''
  foreach ($b in $bytes) { $text += $chars[$b % $chars.Length] }
  return $text
}

function Test-PortFree([int]$Port) {
  $used = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  return ($null -eq $used)
}

# The first free port from $Start (another program may already use 8080 or 5433).
function Find-FreePort([int]$Start) {
  for ($port = $Start; $port -lt $Start + 50; $port++) {
    if (Test-PortFree $port) { return $port }
  }
  throw "No free network port found near $Start."
}

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
  & $Program @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$Program failed (exit code $LASTEXITCODE)." }
}

# ---------------------------------------------------------------------------
# 1. Ports and passwords: created once, then read back on every upgrade.
# ---------------------------------------------------------------------------
$stateFile = Join-Path $ConfigDir 'install.json'
$firstInstall = -not (Test-Path $stateFile)
if ($firstInstall) {
  $state = [pscustomobject]@{
    appPort       = Find-FreePort 8080
    dbPort        = Find-FreePort 5433
    dbName        = $DbName
    dbUser        = $DbName
    dbPassword    = New-Secret 32
    adminPassword = New-Secret 32
    ownerPassword = New-Secret 10
  }
  $state | ConvertTo-Json | Set-Content -Path $stateFile -Encoding ASCII
  # Only administrators and Windows itself may read the passwords.
  Invoke-Checked 'icacls.exe' @($ConfigDir, '/inheritance:r', '/grant:r',
    '*S-1-5-32-544:(OI)(CI)F', '*S-1-5-18:(OI)(CI)F', '/Q')
} else {
  $state = Get-Content -Path $stateFile -Raw | ConvertFrom-Json
  Write-Output "Upgrade: keeping the existing database and settings."
}

# ---------------------------------------------------------------------------
# 2. PostgreSQL: create the database files once, run it as a Windows service.
# ---------------------------------------------------------------------------
# Remembered for the backup below: an upgrade of a PC that already has data.
$hadDatabase = Test-Path (Join-Path $PgData 'PG_VERSION')
if (-not (Test-Path (Join-Path $PgData 'PG_VERSION'))) {
  # initdb refuses to run as administrator: it restarts itself with the
  # Administrators group switched off. So it can't read config\ (admins only)
  # and may not write under ProgramData. Give this user account itself access
  # to an empty database folder and to a private folder for the password file.
  $me = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
  $pwDir = Join-Path $DataDir 'setup-tmp'
  foreach ($dir in @($PgData, $pwDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Invoke-Checked 'icacls.exe' @($dir, '/inheritance:r', '/grant:r', "*${me}:(OI)(CI)F",
      '*S-1-5-32-544:(OI)(CI)F', '*S-1-5-18:(OI)(CI)F', '/Q')
  }
  $pwFile = Join-Path $pwDir 'initdb-password.tmp'
  Set-Content -Path $pwFile -Value $state.adminPassword -Encoding ASCII -NoNewline
  try {
    Invoke-Checked (Join-Path $PgBin 'initdb.exe') @('-D', $PgData, '-U', 'postgres',
      "--pwfile=$pwFile", '-A', 'scram-sha-256', '-E', 'UTF8', '--locale=C')
  } finally {
    Remove-Item -Path $pwDir -Recurse -Force -ErrorAction SilentlyContinue
  }
  # Only this PC can reach the database, on its own port.
  Add-Content -Path (Join-Path $PgData 'postgresql.conf') -Encoding ASCII -Value @(
    '', "# Added by the $AppName installer", "port = $($state.dbPort)",
    "listen_addresses = 'localhost'")
}
# The PostgreSQL service runs as the low-rights NetworkService account.
Invoke-Checked 'icacls.exe' @($PgData, '/grant', '*S-1-5-20:(OI)(CI)F', '/T', '/Q')

if (-not (Get-Service -Name $DbService -ErrorAction SilentlyContinue)) {
  Invoke-Checked (Join-Path $PgBin 'pg_ctl.exe') @('register', '-N', $DbService,
    '-U', 'NT AUTHORITY\NetworkService', '-D', $PgData, '-S', 'auto')
}
Start-Service -Name $DbService

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
  & (Join-Path $PgBin 'pg_isready.exe') -h localhost -p $state.dbPort | Out-Null
  if ($LASTEXITCODE -eq 0) { $ready = $true; break }
  Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'The database did not start. See the logs in pgdata\log.' }

# The app's own database user and database (names are simple, so no quoting is needed).
$psql = Join-Path $PgBin 'psql.exe'
$env:PGPASSWORD = $state.adminPassword
try {
  $connect = @('-h', 'localhost', '-p', "$($state.dbPort)", '-U', 'postgres', '-d', 'postgres', '-tAc')
  $roleExists = & $psql @connect "SELECT 1 FROM pg_roles WHERE rolname = '$($state.dbUser)'"
  if ("$roleExists".Trim() -ne '1') {
    Invoke-Checked $psql ($connect + "CREATE ROLE $($state.dbUser) LOGIN PASSWORD '$($state.dbPassword)'")
  }
  $dbExists = & $psql @connect "SELECT 1 FROM pg_database WHERE datname = '$($state.dbName)'"
  if ("$dbExists".Trim() -ne '1') {
    Invoke-Checked $psql ($connect + "CREATE DATABASE $($state.dbName) OWNER $($state.dbUser) ENCODING 'UTF8' TEMPLATE template0")
  }
} finally {
  Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

# Upgrades: back up the database before the new app starts (its first start
# runs the new database migrations). A failed backup stops the setup here, so
# the client's data is never changed without a fresh backup.
if ($hadDatabase) {
  Invoke-Checked 'powershell.exe' @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
    (Join-Path $InstallDir 'backup.ps1'), '-BeforeUpgrade')
  Write-Output 'Database backed up before the upgrade (backups\before-upgrade-*.dump).'
}

# ---------------------------------------------------------------------------
# 3. The app's settings: application.properties, read by Spring Boot on start.
#    Written from the project's .env.example the first time; later upgrades
#    only add settings that are new, so the owner's edits are kept.
# ---------------------------------------------------------------------------
$propsFile = Join-Path $ConfigDir 'application.properties'
$existing = @{}
if (Test-Path $propsFile) {
  foreach ($line in Get-Content -Path $propsFile) {
    if ($line -match '^\s*([^#=\s][^=]*)=') { $existing[$Matches[1].Trim()] = $true }
  }
}

# Values the installer decides; everything else keeps the example value.
function Get-SettingValue([string]$Key, [string]$Example) {
  switch -Regex ($Key) {
    '^(DB_NAME|POSTGRES_DB)$' { return $state.dbName }
    '^(DB_USER|DB_USERNAME|POSTGRES_USER|SPRING_DATASOURCE_USERNAME)$' { return $state.dbUser }
    '^(DB_PASSWORD|POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD)$' { return $state.dbPassword }
    '^DB_HOST$' { return 'localhost' }
    '^DB_PORT$' { return "$($state.dbPort)" }
    '^SPRING_DATASOURCE_URL$' { return "jdbc:postgresql://localhost:$($state.dbPort)/$($state.dbName)" }
    '^(PORT|SERVER_PORT)$' { return "$($state.appPort)" }
    '^OWNER_PASSWORD$' { return $state.ownerPassword }
  }
  # Placeholder secrets ("change-me...") become long random ones.
  if ($Key -match 'SECRET|TOKEN|KEY' -and $Example -match 'change-me') { return New-Secret 48 }
  return $Example
}

$isNew = ($existing.Count -eq 0)
$newLines = @()
if ($isNew) {
  $newLines += "# Settings for $AppName on this computer, written by the installer."
  $newLines += "# After changing a value, restart the PC (or the ""$Slug"" service)."
  $newLines += ''
}
$example = Join-Path $InstallDir 'settings-example.env'
if (Test-Path $example) {
  foreach ($line in Get-Content -Path $example) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_.]*)\s*=(.*)$') {
      $key = $Matches[1]
      $value = $Matches[2].Trim()
      if (-not $existing.ContainsKey($key)) {
        $newLines += "$key=$(Get-SettingValue $key $value)"
        $existing[$key] = $true
      }
    } elseif ($isNew) {
      $newLines += $line # comments and blank lines, first install only
    }
  }
}
# These always point at the database and port made above.
$fixed = [ordered]@{
  'spring.datasource.url'      = "jdbc:postgresql://localhost:$($state.dbPort)/$($state.dbName)"
  'spring.datasource.username' = $state.dbUser
  'spring.datasource.password' = $state.dbPassword
  'server.port'                = "$($state.appPort)"
  'logging.file.name'          = ((Join-Path $Logs 'app.log') -replace '\\', '/')
}
foreach ($key in $fixed.Keys) {
  if (-not $existing.ContainsKey($key)) { $newLines += "$key=$($fixed[$key])" }
}
if ($newLines.Count -gt 0) {
  # UTF-8 without the byte-order mark Windows PowerShell adds, so Spring Boot reads it cleanly.
  [IO.File]::AppendAllLines($propsFile, [string[]]$newLines, (New-Object Text.UTF8Encoding $false))
}

# ---------------------------------------------------------------------------
# 4. The app as a Windows service (WinSW runs java -jar and restarts it on a crash).
# ---------------------------------------------------------------------------
$serviceExe = Join-Path $InstallDir 'app\service.exe'
$esc = { param($text) [Security.SecurityElement]::Escape($text) }
$configUrl = 'optional:file:' + (($ConfigDir -replace '\\', '/') + '/')
$javaArgs = "-XX:MaxRAMPercentage=40 -jar ""$InstallDir\app\app.jar"" --spring.profiles.active=prod --spring.config.additional-location=$configUrl"
@"
<service>
  <id>$Slug</id>
  <name>$(& $esc $AppName)</name>
  <description>$(& $esc $AppName). Settings: $(& $esc $propsFile)</description>
  <executable>$(& $esc (Join-Path $InstallDir 'java\bin\java.exe'))</executable>
  <arguments>$(& $esc $javaArgs)</arguments>
  <workingdirectory>$(& $esc $DataDir)</workingdirectory>
  <logpath>$(& $esc $Logs)</logpath>
  <log mode="roll-by-size"><sizeThreshold>10240</sizeThreshold><keepFiles>5</keepFiles></log>
  <depend>$DbService</depend>
  <startmode>Automatic</startmode>
  <onfailure action="restart" delay="10 sec"/>
</service>
"@ | Set-Content -Path (Join-Path $InstallDir 'app\service.xml') -Encoding UTF8

if (Get-Service -Name $Slug -ErrorAction SilentlyContinue) {
  Invoke-Checked $serviceExe @('refresh')
} else {
  Invoke-Checked $serviceExe @('install')
}
Start-Service -Name $Slug

# Phones and tablets on the shop's network may open the app (private networks only).
$ruleName = "$AppName (port $($state.appPort))"
if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
  New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP `
    -LocalPort $state.appPort -Action Allow -Profile Private, Domain | Out-Null
}

# ---------------------------------------------------------------------------
# 5. Shortcuts, nightly backup, first-login note.
# ---------------------------------------------------------------------------
$url = "http://localhost:$($state.appPort)/"
$shortcut = "[InternetShortcut]`r`nURL=$url`r`n"
$desktop = [Environment]::GetFolderPath('CommonDesktopDirectory')
$startMenu = [Environment]::GetFolderPath('CommonPrograms')
foreach ($folder in @($desktop, $startMenu, $DataDir)) {
  Set-Content -Path (Join-Path $folder "$AppName.url") -Value $shortcut -Encoding ASCII
}

$backupAction = New-ScheduledTaskAction -Execute 'powershell.exe' `
  -Argument "-NoProfile -ExecutionPolicy Bypass -File ""$InstallDir\backup.ps1"""
$backupTrigger = New-ScheduledTaskTrigger -Daily -At '21:00'
$backupSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable
Register-ScheduledTask -TaskName "$AppName backup" -Action $backupAction -Trigger $backupTrigger `
  -Settings $backupSettings -User 'SYSTEM' -RunLevel Highest -Force | Out-Null

$lanAddress = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
  Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
  Select-Object -First 1 -ExpandProperty IPAddress
if ($firstInstall) {
  $ownerLine = ''
  $ownerName = Select-String -Path $propsFile -Pattern '^OWNER_USERNAME=(.*)$' | Select-Object -First 1
  if ($ownerName) {
    $ownerLine = "Username: $($ownerName.Matches[0].Groups[1].Value)`r`nPassword: $($state.ownerPassword)`r`n`r`nChange this password after your first login, then delete this file.`r`n"
  }
  $phoneLine = ''
  if ($lanAddress) { $phoneLine = "Phones and tablets on the same Wi-Fi: http://$($lanAddress):$($state.appPort)/`r`n" }
  $note = "$AppName is installed.`r`n`r`nOpen it: $url  (or the $AppName icon on the desktop)`r`n$phoneLine`r`n$ownerLine`r`nBackups are made every night at 21:00 in $Backups`r`nCopy them to a flash disk now and then.`r`n"
  Set-Content -Path (Join-Path $desktop "$AppName - first login.txt") -Value $note -Encoding UTF8
}

# ---------------------------------------------------------------------------
# 6. Wait until the app answers (the first start runs the database migrations).
# ---------------------------------------------------------------------------
$started = $false
for ($i = 0; $i -lt 90; $i++) {
  $code = & curl.exe -s -o NUL -w '%{http_code}' $url
  if ($code -and $code -ne '000') { $started = $true; break }
  Start-Sleep -Seconds 2
}
if (-not $started) { throw "$AppName did not start. See $Logs." }
Write-Output "$AppName is running at $url"
Stop-Transcript | Out-Null
