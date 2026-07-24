@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0COMPILAR_Y_VERIFICAR_LOCAL.ps1"
set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" echo Build stopped with code %EXITCODE%.
if "%EXITCODE%"=="0" echo APK built and verified successfully.
pause
exit /b %EXITCODE%
