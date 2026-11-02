@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Shuffleframe - Restore existing cache to v0.7
set "PKG=com.hasbreo.shuffleframe"
set "BACKUP=%~dp0Shuffleframe-v0.6-backup.tar"
set "APK=%~dp0Shuffleframe-v0.7.0.apk"
echo SHUFFLEFRAME CACHE MIGRATION - THIS CAN UNINSTALL THE OLD APP
where adb >nul 2>nul
if errorlevel 1 (echo ERROR: adb.exe missing. & exit /b 1)
where tar >nul 2>nul
if errorlevel 1 (echo ERROR: tar.exe missing. & exit /b 1)
if not exist "%BACKUP%" (echo ERROR: Backup missing. Nothing changed. & exit /b 1)
if not exist "%APK%" (echo ERROR: Put Shuffleframe-v0.7.0.apk in this folder. Nothing changed. & exit /b 1)
tar -tf "%BACKUP%" >nul
if errorlevel 1 (echo ERROR: Backup could not be read. Nothing changed. & exit /b 1)
powershell -NoProfile -Command "if ((Get-Item -LiteralPath '%BACKUP%').Length -lt 1048576) { exit 2 }"
if errorlevel 1 (echo ERROR: Backup is too small. Nothing changed. & exit /b 1)
adb get-state 2>nul | findstr /x "device" >nul
if errorlevel 1 (echo ERROR: Device not connected. & exit /b 1)
echo Attempting a non-destructive in-place installation first...
adb install -r "%APK%"
if not errorlevel 1 (
  echo In-place update succeeded; old data should be intact. No restore required.
  exit /b 0
)
echo.
echo The original v0.6 APK was signed with a different certificate.
echo The verified backup remains safe on your PC:
echo "%BACKUP%"
echo WARNING: Uninstalling the installed app erases its private data.
echo The backup is an attempt to preserve it, not a guaranteed restoration.
echo Only proceed if you have copied the backup elsewhere as well.
set /p "CONFIRM=Type RESTORE to uninstall v0.6, install v0.7 and restore files: "
if /I not "%CONFIRM%"=="RESTORE" (echo Cancelled. Existing app unchanged. & exit /b 0)
adb uninstall %PKG%
if errorlevel 1 (echo ERROR: Uninstall failed. Backup remains on PC. & exit /b 1)
adb install "%APK%"
if errorlevel 1 (echo ERROR: Install failed. Keep your PC backup. & exit /b 1)
echo Restoring cached photos and settings into v0.7...
adb shell -T run-as %PKG% tar -xf - < "%BACKUP%"
if errorlevel 1 (echo ERROR: Restore failed. Keep backup. & exit /b 1)
adb shell run-as %PKG% ls files/photos >nul
if errorlevel 1 (echo ERROR: Restored cached photos not found. Keep backup. & exit /b 1)
echo Restore completed. Open the app and verify all album counts before deleting the backup.
exit /b 0
