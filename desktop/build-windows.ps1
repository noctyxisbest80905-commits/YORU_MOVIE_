$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
Write-Host 'Building Yoru Desktop for Windows x64...' -ForegroundColor Cyan
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) { throw 'Rust is required. Install stable Rust from https://rustup.rs with the MSVC toolchain.' }
if (-not (Get-Command py -ErrorAction SilentlyContinue)) { throw 'Python 3.12 x64 is required. Install it from https://python.org, including the Python launcher.' }
function Check-Step([string]$Name) { if ($LASTEXITCODE -ne 0) { throw "$Name failed (exit $LASTEXITCODE)." } }
$env:RUSTFLAGS = ($env:RUSTFLAGS + ' -C target-feature=+crt-static').Trim()
cargo build --release --locked --target x86_64-pc-windows-msvc --bin moviebox-desktop-bridge
Check-Step 'Rust desktop backend build'
New-Item -ItemType Directory -Force "$Root\dist" | Out-Null
New-Item -ItemType Directory -Force "$PSScriptRoot\bin" | Out-Null
Copy-Item "$Root\target\x86_64-pc-windows-msvc\release\moviebox-desktop-bridge.exe" "$PSScriptRoot\bin\moviebox-desktop-bridge.exe" -Force
py -3.12 -m venv "$PSScriptRoot\.venv"
Check-Step 'Python environment setup'
$Python = "$PSScriptRoot\.venv\Scripts\python.exe"
& $Python -m pip install -r "$PSScriptRoot\requirements.txt"
Check-Step 'Python dependencies'
& $Python -m unittest discover -s "$PSScriptRoot\tests" -v
Check-Step 'Desktop unit tests'
& $Python -m PyInstaller --noconfirm --clean --onefile --windowed --name Yoru --distpath "$Root\dist" --workpath "$PSScriptRoot\build" --specpath "$PSScriptRoot" --add-data "$PSScriptRoot\web;web" --add-data "$PSScriptRoot\adult_anime_index.json;." --add-data "$PSScriptRoot\ANIME-INDEX-NOTICE.txt;." --add-data "$PSScriptRoot\ANIME-INDEX-LICENSE.txt;." --add-binary "$PSScriptRoot\bin\moviebox-desktop-bridge.exe;bin" "$PSScriptRoot\app.py"
Check-Step 'Windows executable packaging'
& $Python "$PSScriptRoot\collect-licenses.py" "$Root\dist\THIRD-PARTY-LICENSES.txt"
Check-Step 'Third-party license collection'
Copy-Item "$PSScriptRoot\adult_anime_index.json","$PSScriptRoot\ANIME-INDEX-NOTICE.txt","$PSScriptRoot\ANIME-INDEX-LICENSE.txt" "$Root\dist" -Force
Copy-Item "$Root\LICENSE-MIT","$Root\LICENSE-APACHE","$PSScriptRoot\CREDITS.txt" "$Root\dist" -Force
Write-Host "Ready: $Root\dist\Yoru.exe" -ForegroundColor Green
Write-Host 'This is an unsigned portable build. It requires WebView2 plus VLC or mpv for playback.'
