# Windows installer for POS System for Tavuzi Gadgets

Written by BS Codes. One setup file that installs everything the client's PC needs:
the app, its own Java runtime and PostgreSQL. Nothing else has to be installed first.

## Making a new installer

In BS Codes: open the project → **Build** tab → **Build Windows installer**. BS Codes
pushes the code with an `installer-…` tag; GitHub builds the `.exe` on a Windows machine
(`.github/workflows/windows-installer.yml`), installs it there as a test, and attaches it to
a GitHub release. BS Codes shows the download link when it is ready (about 15 minutes).

## Installing on the client's PC

1. Copy `pos-system-for-tavuzi-gadgets-setup-<version>.exe` to the PC (flash disk or download) and double-click it.
2. Click through the steps (it needs an administrator account).
3. When it finishes, the app opens in the browser. A desktop file
   **"POS System for Tavuzi Gadgets - first login.txt"** has the owner's username and password: log in, change
   the password, then delete that file.

## What goes where

| What                                    | Where                                                   |
| --------------------------------------- | ------------------------------------------------------- |
| App, Java, PostgreSQL                   | `C:\Program Files\POS System for Tavuzi Gadgets\`                         |
| Settings (shop details, M-Pesa keys, …) | `C:\ProgramData\pos-system-for-tavuzi-gadgets\config\application.properties` |
| Ports and generated passwords           | `C:\ProgramData\pos-system-for-tavuzi-gadgets\config\install.json`           |
| Database files                          | `C:\ProgramData\pos-system-for-tavuzi-gadgets\pgdata\`                       |
| Nightly backups (21:00, newest 14 kept) | `C:\ProgramData\pos-system-for-tavuzi-gadgets\backups\`                      |
| Logs (setup, app, backup)               | `C:\ProgramData\pos-system-for-tavuzi-gadgets\logs\`                         |

- Database: `pos_system_for_tavuzi_gadgets`, user `pos_system_for_tavuzi_gadgets`, on `localhost` port 5433 (or the next free port).
- Windows services: `pos-system-for-tavuzi-gadgets-db` (PostgreSQL) and `pos-system-for-tavuzi-gadgets` (the app). Both start with the PC.
- The app listens on port 8080 (or the next free port); phones and tablets on the same Wi-Fi
  can open `http://<pc-address>:<port>/`.

## Changing a setting

Edit `application.properties` as administrator (Notepad → Run as administrator), save, then
restart the PC or the `pos-system-for-tavuzi-gadgets` service (Services app → right-click → Restart).

## Upgrading

Run the newer setup file on the PC. The database, passwords and settings are kept; new
settings from `.env.example` are added with their example values.

## Restoring a backup

In PowerShell as administrator (replace the file name and the port/password from `install.json`):

```powershell
Stop-Service pos-system-for-tavuzi-gadgets
$env:PGPASSWORD = '<dbPassword from install.json>'
& 'C:\Program Files\POS System for Tavuzi Gadgets\pgsql\bin\pg_restore.exe' -h localhost -p 5433 -U pos_system_for_tavuzi_gadgets -d pos_system_for_tavuzi_gadgets --clean --if-exists 'C:\ProgramData\pos-system-for-tavuzi-gadgets\backups\<file>.dump'
Start-Service pos-system-for-tavuzi-gadgets
```

## Uninstalling

Settings → Apps → POS System for Tavuzi Gadgets → Uninstall. The data in `C:\ProgramData\pos-system-for-tavuzi-gadgets` is kept on
purpose; delete that folder by hand only if the records are truly not needed.
