$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
& "$Root\desktop\build-windows.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Yoru executable build failed.' }
$Compiler = (Get-Command makensis.exe -ErrorAction SilentlyContinue).Source
if (-not $Compiler) { $Compiler = "${env:ProgramFiles(x86)}\NSIS\makensis.exe" }
if (-not (Test-Path -LiteralPath $Compiler)) { throw 'Install NSIS 3.x, then rerun installer/build-setup.ps1.' }
& $Compiler /V3 "/DPROJECT_DIR=$Root" "/DINPUT_DIR=$Root\dist" "/DOUTPUT_FILE=$Root\dist\Yoru-Setup.exe" "$PSScriptRoot\Yoru.nsi"
if ($LASTEXITCODE -ne 0) { throw 'Windows installer compilation failed.' }
if (-not (Test-Path "$Root\dist\Yoru-Setup.exe")) { throw 'Setup output missing.' }
(Get-FileHash "$Root\dist\Yoru-Setup.exe" -Algorithm SHA256).Hash | Set-Content "$Root\dist\Yoru-Setup.exe.sha256"
Write-Host "Ready: $Root\dist\Yoru-Setup.exe" -ForegroundColor Green
