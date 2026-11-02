param([Parameter(Mandatory=$true)][ValidateSet('WebView2','VLC')][string]$Kind)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
function Has-WebView {
    $guid = '{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}'
    foreach ($key in @("HKLM:\SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\$guid", "HKCU:\Software\Microsoft\EdgeUpdate\Clients\$guid")) {
        try { $v = (Get-ItemProperty -LiteralPath $key -Name pv -ErrorAction Stop).pv; if ([version]$v -gt [version]'0.0.0.0') { return $true } } catch {}
    }
    return $false
}
function Has-VLC {
    foreach ($path in @("$env:ProgramFiles\VideoLAN\VLC\vlc.exe", "${env:ProgramFiles(x86)}\VideoLAN\VLC\vlc.exe")) { if (Test-Path -LiteralPath $path) { return $true } }
    return [bool](Get-Command vlc.exe -ErrorAction SilentlyContinue)
}
$cache = Join-Path ([IO.Path]::GetTempPath()) ('YoruSetup-' + [Guid]::NewGuid().ToString('N'))
try {
    if (($Kind -eq 'WebView2' -and (Has-WebView)) -or ($Kind -eq 'VLC' -and (Has-VLC))) { Write-Host "$Kind already installed; keeping it unchanged."; exit 0 }
    New-Item -ItemType Directory -Path $cache | Out-Null
    $file = Join-Path $cache 'official-installer.exe'
    if ($Kind -eq 'VLC') {
        # Pinned official release. Update version AND SHA256 together after verifying the vendor release.
        $url = 'https://download.videolan.org/pub/videolan/vlc/3.0.24/win64/vlc-3.0.24-win64.exe'
        $expected = 'D711E1E1FE52052748C39080C7DCE63F6B7E4C315EFEDF1774A3F1957B782FF3'
        $publisher = 'VideoLAN'
    } else {
        $url = 'https://go.microsoft.com/fwlink/p/?LinkId=2124703'
        $publisher = 'Microsoft Corporation'
    }
    Write-Host "Downloading $Kind from its official distributor... Internet access is required."
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $file -TimeoutSec 240
    if ($Kind -eq 'VLC' -and (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $expected) { throw 'VLC checksum did not match the pinned official release. Nothing was executed.' }
    $signature = Get-AuthenticodeSignature -LiteralPath $file
    if ($signature.Status -ne 'Valid' -or -not $signature.SignerCertificate -or $signature.SignerCertificate.Subject -notlike "*$publisher*") { throw 'Official publisher signature could not be verified. Nothing was executed.' }
    if ($Kind -eq 'VLC') {
        Write-Host 'Opening the official VLC wizard. Approve its Windows prompt only if you wish to install it.'
        $process = Start-Process -FilePath $file -Verb RunAs -Wait -PassThru
    } else {
        $process = Start-Process -FilePath $file -ArgumentList '/silent','/install' -Wait -PassThru
    }
    if ($process.ExitCode -notin @(0,3010)) { throw "$Kind installer exited with code $($process.ExitCode)." }
    if ($Kind -eq 'WebView2') {
        for ($i=0; $i -lt 20 -and -not (Has-WebView); $i++) { Start-Sleep -Milliseconds 500 }
        if (-not (Has-WebView)) { throw 'WebView2 is still missing. Install the official Microsoft WebView2 Runtime and rerun setup.' }
    }
    Write-Host "$Kind installation finished."
    exit 0
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally {
    Remove-Item -LiteralPath $cache -Recurse -Force -ErrorAction SilentlyContinue
}
