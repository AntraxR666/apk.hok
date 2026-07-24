@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0SUBIR_A_APK_HOK.ps1"
set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" echo Upload stopped with code %EXITCODE%.
if "%EXITCODE%"=="0" echo Upload finished. Open GitHub Actions and wait for Android CI.
pause
exit /b %EXITCODE%
