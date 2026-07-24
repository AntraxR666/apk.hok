@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0CREAR_REPO_PRIVADO_Y_SUBIR.ps1"
set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" echo Repository setup stopped with code %EXITCODE%.
if "%EXITCODE%"=="0" echo Repository created or updated successfully.
pause
exit /b %EXITCODE%
