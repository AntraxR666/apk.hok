@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0APLICAR_V4_Y_COMPILAR.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
if "%RESULT%"=="0" (
  echo V4 completed successfully.
) else (
  echo V4 stopped with code %RESULT%.
)
echo Press any key to close this window.
pause >nul
exit /b %RESULT%
