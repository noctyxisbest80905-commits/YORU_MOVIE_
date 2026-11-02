# Yoru — created by Obscura

## Always-on explicit-content filter

This filtered update checks search/discovery metadata, title aliases and release
filenames before display or playback. Unverified search/discovery results stay
hidden. There is no in-app disable switch. See `../safety/README.md` for behavior,
privacy, performance, collision handling and known limitations. It is not a guarantee
against all inappropriate content. The Windows source must be rebuilt; downloading
this ZIP alone does not update an old EXE. The filtered installer no longer ships
the unfiltered upstream terminal client.



A graphical window for the included MovieBox-TUI Rust application. The original
terminal UI and provider code are preserved. The new desktop UI calls that same
Rust library through a small private JSON bridge; it does not scrape pages in a
browser, send commands to a terminal, or embed a terminal window.

## Updating an existing repository

This source overlay adds Yoru branding, credits, local watch history and Continue
watching, plus a Windows installation wizard. Merge its files into the existing
full MovieBox-TUI repository; do not delete the original source or Cargo files.
Follow `installer/README.txt`, then run **Build Yoru Windows Setup** in Actions.
Download the artifact and extract `Yoru-Setup.exe`. No ready-to-run Windows EXE is
included in this source update.

## Watch history and credits

VLC/mpv launches save actual player-reported positions, audio edition, season,
episode and release selection. Continue watching offers Resume and Start over;
History provides per-item removal and confirmed clearing. See the installer README
for tracking limitations and local data locations. Settings → About Yoru & Credits
credits Obscura, configured providers, mirrors, upstream contributors and runtimes.

## Audio languages: what the selector actually does

- If the provider returns separate dubbed-edition IDs, choosing Hindi or English
  loads details/episodes and streams for that edition. Captions use the selected ID.
- Otherwise, explicitly reported `Release.language` metadata supplies the available
  choices and filters matching releases. Unknown language metadata is not guessed.
- A known selected language is passed to mpv (`--alang`) or VLC
  (`--audio-language`) as an embedded-track preference. A player can fall back when
  the requested embedded track is absent. This is not dubbing, translation, or a
  guarantee that a stream contains a language. Your player's Audio menu remains
  available for inspection/selection of actual embedded tracks.
- Preferred audio and preferred quality in Settings only apply when offered.
- MovieBox supplies the discovery feed; other providers are available through search.
- The cinematic welcome artwork is generated; title posters are fetched from the
  provider. No films, audio tracks, or media downloads are bundled in the update.

## What is included

- Search across MovieBox, 4KHDHub, Dramachi, CircleFTP, and DhakaFlix.
- Title details and provider-supplied artwork; movie/series and season/episode selection.
- Available releases/quality picker, MovieBox subtitles when provided.
- Launch streams in a detected VLC or mpv installation.
- Provider-reported audio-language selection (including Hindi/English when available), dubbed-edition switching, and embedded-audio language preferences for VLC/mpv.
- Local favorites and saved preferred player, audio language, and quality.
- Dark cinematic appearance requested by the user, a visible top-bar Settings button, keyboard navigation, responsive layout,
  loading/empty/error states, stale-request protection, and request timeouts.
- A double-click Windows build script and a manually triggered GitHub Actions build.

**Not yet in the desktop UI:** downloads, Live TV/IPTV, Stremio addon management,
or importing the terminal app's library.
The original terminal app still provides its existing features.

## Important delivery status

This archive contains the **desktop source and Windows build workflow, not a compiled
Windows executable**. The Linux checks are recorded in `VALIDATION.md`. A successful
Windows workflow/build is still required to produce the `.exe`; the native Windows
WebView and actual media playback must then be tested on Windows. No compiled installer,
media player, or Python/Rust toolchain is bundled in this source archive.

## Get the Windows setup wizard

Follow `installer/README.txt`. Merge this overlay into your existing full repository,
then run **Actions → Build Yoru Windows Setup → Run workflow**. Download the
**Yoru-Windows-Setup-x64** artifact after a successful run, extract it and run
**Yoru-Setup.exe**. The workflow lives at `.github/workflows/yoru-windows.yml`;
`installer/yoru-windows.yml` is an identical visible copy for manual upload.

## Build locally on Windows 10/11 x64

Install these prerequisites from their official sources:

- **Rust stable, version 1.90 or later**, MSVC toolchain: https://rustup.rs
- **Visual Studio Build Tools 2022**, with **Desktop development with C++** and a
  Windows SDK: https://visualstudio.microsoft.com/visual-cpp-build-tools/
- **Python 3.12 x64**, including the `py` launcher: https://www.python.org/downloads/
- Internet access for Cargo and pip dependencies; sufficient free disk space for Rust builds.

Restart Windows or your session after installing tools so PATH updates are picked up.
Double-click **BUILD-WINDOWS.cmd** in the project root. It builds the Rust bridge,
creates a Python environment, runs unit tests, and packages a one-file GUI application.
The output is **dist\Yoru.exe**. Subsequent launches do not require Python
or Rust. Compilation can take several minutes; the script does not hide build errors.

The build script respects your PowerShell policy. If organizational policy blocks
scripts, ask your administrator for an approved way to run it; do not disable the policy.

## Run the finished app

Runtime requirements:

- Windows 10/11 x64 and **Microsoft Edge WebView2 Runtime** (commonly already present):
  https://developer.microsoft.com/microsoft-edge/webview2/
- **VLC** or **mpv** installed in a standard location or available through PATH.
  Prefer mpv for complex streams. No media player is bundled.
- An internet connection and a provider accessible from your network. BDIX sources
  require the appropriate network. Provider outages/changes can break search/playback.

1. Open the EXE. Search a title and select a provider.
2. Click a result, choose a season/episode when relevant, and select a release.
3. Choose a detected player and optional subtitle, then click **Open in player**.
4. Use **Save to favorites** to keep a title in your local library.

The video opens in a separate player window, not inside Yoru. Success
means the player process was launched; the app cannot verify that a third-party
stream subsequently decodes or remains available. Playback settings can rescan players.

The EXE is unsigned. Windows may show a reputation warning; this package does not
include code signing. Verify the source/build provenance and follow your device's
security policy. No instructions to bypass endpoint controls are needed.

One-file packaging extracts its private backend/resources to a temporary directory on
launch, so first launch can take a moment. Avoid concurrent instances while editing
favorites. The original VLC/DASH sidecar can continue temporarily after closing the
UI while a player uses it, and exits after the upstream idle timeout.

## Local data and privacy

Desktop favorites/settings on Windows:
`%LOCALAPPDATA%\MovieBoxDesktop\favorites.json` and `settings.json`.
Delete those files to reset the desktop library/preferences. Keep a backup if wanted.
The terminal app's history/favorites are not imported or overwritten. The reused
Rust backend can use its existing MovieBox-TUI config/cache locations for sessions
and downloaded subtitles. Searches and playback contact the selected content provider;
poster images are fetched from the provider's HTTPS image hosts. No telemetry is added.

Only access content you have permission to watch and comply with applicable law.
This project does not host or bundle films or media streams.

## Developer notes

- `desktop/app.py`: pywebview window, allowlisted request dispatch, subprocess
  lifecycle/timeout handling, atomic local favorites/settings writes.
- `desktop/web/`: static UI; provider-supplied text is inserted with `textContent`,
  not HTML. No public HTTP server is opened.
- `src/bin/moviebox-desktop-bridge.rs`: typed Rust backend using existing providers,
  release resolver, captions, player detection, and VLC/DASH proxy sidecar.
- `desktop/tests/test_api.py`: Python unit tests (no network/player dependency).
- `desktop/tests/ui-test.cjs`: browser interaction tests using explicit mock provider
  responses, NOT live-content or native-WebView tests. Requires the preinstalled
  Playwright/Chromium equivalents on the developer's machine.

`cargo check --locked --bin moviebox-desktop-bridge` checks the Rust bridge.
`python -m unittest discover -s desktop/tests -v` runs the Python unit tests.
To run from source, build the bridge, put it in `desktop/bin/`, install
`desktop/requirements.txt`, and run `python desktop/app.py`. Linux additionally
requires a pywebview-supported GTK/Qt environment; Linux packaging is not included.

Original MovieBox-TUI is MIT OR Apache-2.0; see the included license files. Dependencies
have their own licenses. New desktop files are offered under the same dual license.
