@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Shuffleframe - Back up installed v0.6 without changing it
set "PKG=com.hasbreo.shuffleframe"
set "BACKUP=%~dp0Shuffleframe-v0.6-backup.tar"
set "MANIFEST=%~dp0Shuffleframe-v0.6-backup-files.txt"
echo.
echo SHUFFLEFRAME SAFE LOCAL BACKUP
echo The existing app will NOT be uninstalled or changed.
where adb >nul 2>nul
if errorlevel 1 (echo ERROR: adb.exe not found in PATH. & exit /b 1)
where tar >nul 2>nul
if errorlevel 1 (echo ERROR: Windows tar.exe not found. & exit /b 1)
if exist "%BACKUP%" (echo ERROR: A backup already exists. It will not be overwritten. & exit /b 1)
adb get-state 2>nul | findstr /x "device" >nul
if errorlevel 1 (echo ERROR: Phone not authorised for USB debugging. & exit /b 1)
adb shell run-as %PKG% id >nul 2>nul
if errorlevel 1 (echo ERROR: Existing Shuffleframe app not accessible through adb run-as. & exit /b 1)
echo Copying app cache and album settings to this PC...
adb exec-out run-as %PKG% tar -cf - files shared_prefs > "%BACKUP%"
if errorlevel 1 (echo ERROR: Backup failed. Existing app untouched. & exit /b 1)
powershell -NoProfile -Command "if ((Get-Item -LiteralPath '%BACKUP%').Length -lt 1048576) { exit 2 }"
if errorlevel 1 (echo ERROR: Archive unexpectedly small. Do not uninstall. & exit /b 1)
tar -tf "%BACKUP%" > "%MANIFEST%"
if errorlevel 1 (echo ERROR: Archive failed verification. Do not uninstall. & exit /b 1)
findstr /b /c:"files/photos/" "%MANIFEST%" >nul
if errorlevel 1 (echo ERROR: No cached media in archive. Do not uninstall. & exit /b 1)
findstr /b /c:"shared_prefs/" "%MANIFEST%" >nul
if errorlevel 1 (echo ERROR: No app settings in archive. Do not uninstall. & exit /b 1)
echo.
echo BACKUP VERIFIED: "%BACKUP%"
echo Keep this file and a second copy. Your old app remains installed.
exit /b 0
