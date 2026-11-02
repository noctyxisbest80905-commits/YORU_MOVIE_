@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -File "%~dp0desktop\build-windows.ps1"
if errorlevel 1 (
  echo.
  echo Build failed. Read the message above. See desktop\README.md for prerequisites.
  pause
  exit /b 1
)
echo.
echo Double-click dist\Yoru.exe to open the app.
pause
