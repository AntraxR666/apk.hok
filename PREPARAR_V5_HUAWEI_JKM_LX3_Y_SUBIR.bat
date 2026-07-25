@echo off
setlocal
cd /d "%~dp0"
set "SCRIPT=%~dp0PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.ps1"

if not exist "%SCRIPT%" (
  echo.
  echo ERROR: The PowerShell launcher was not found:
  echo %SCRIPT%
  echo.
  echo Press any key to close this window.
  pause >nul
  exit /b 2
)

echo ============================================================
echo  HOK V5 HUAWEI JKM-LX3 - CLEAN CLONE DELIVERY V3
echo ============================================================
echo.
echo Do not run this file as Administrator.
echo Do not close this window.
echo The launcher will create a fresh temporary Git clone.
echo It will not use any previous project folder.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
set "RESULT=%ERRORLEVEL%"

echo.
if "%RESULT%"=="0" (
  echo V5 Huawei delivery completed successfully.
) else (
  echo V5 Huawei delivery stopped with code %RESULT%.
  echo No valid APK was declared by the launcher.
)
echo.
echo Press any key to close this window.
pause >nul
exit /b %RESULT%
