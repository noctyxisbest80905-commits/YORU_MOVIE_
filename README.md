# Yoru — created by Obscura

Cumulative PC, Android phone/tablet and Android TV source, including the Yoru rename and credits update and all subsequent changes in this delivery.

## Included
- Yoru branding and About Yoru & Credits, crediting Obscura, configured providers, mirror support, upstream contributors and runtime projects.
- Providers: MovieBox, 4KHDHub, Dramachi, CircleFTP and DhakaFlix. Availability and BDIX network requirements still apply.
- Search, details, provider-reported audio-language/quality selection, favorites and saved preferences.
- Continue watching, local watch history, resume/start over, and removal/confirmed clearing.
- Always-on explicit-content filtering in the PC, phone and TV apps, including checked metadata and the offline explicit-anime alias index. Ordinary anime is not blanket-blocked. See safety/README.md for limitations; this is not a guarantee against mislabeled/new content or a parental-control certification.
- Android v0.4 adds offline downloads, pause/resume/removal, cache-only offline playback, immersive phone playback, Fit/Fill, phone rotation and control lock. See ANDROID_OFFLINE_FULLSCREEN.md.
- Windows build scripts and the setup-wizard workflow. No compiled Windows EXE is included in this delivery.

## GitHub builds
After committing these files to your repository, open Actions and run:
- **Build Yoru Windows Setup** → `Yoru-Windows-Setup-x64` artifact → extract `Yoru-Setup.exe`.
- **Build Yoru Android Mobile** → `Yoru-Android-Mobile-filtered` artifact.
- **Build Yoru Android TV** → `Yoru-Android-TV-filtered` artifact.

The Android artifact names retain the word `filtered`; the source version is now versionCode 4. Native libraries are intentionally excluded from source delivery and rebuilt from the included Rust source by the build scripts. Build tools/dependencies are fetched during GitHub builds.

### Android signing
The APKs in the outer archive's `installers/` folder are already signed, non-debuggable v0.4 builds. Their certificates match the earlier filtered v0.3 APKs. The provided GitHub Android workflows currently generate debug-signed APKs using the runner's key; they do **not** reproduce the bundled APK signing identity. Set up your own stable private release signing configuration before distributing repeatable production updates. Private signing keys/passwords are not included and must not be committed. A differently signed build cannot replace an installed app in place; uninstalling removes its local data.

## Scope and verification
PC changes are source-only and need a Windows build. Downloads and immersive fullscreen changes apply to Android, not the PC UI. Android native playback/download completion and OEM system-bar behavior require on-device verification. Automated Java/browser/transport checks are documented in ANDROID_OFFLINE_FULLSCREEN.md and VALIDATION-CUMULATIVE.json.

The original upstream terminal sources, internal crate/JNI identifiers and upstream documentation remain for compatibility and attribution. They are not a newly filtered Yoru terminal app; the filtered Windows installer does not bundle that unfiltered terminal. Original project documentation is preserved as README-UPSTREAM.md. Existing MIT/Apache and dataset notices are retained. Use only content you are authorized to access or save.
