# Yoru Android v0.4 — offline downloads and immersive playback

Phone + Android TV APKs. Android 8+; ARM64, ARMv7 and x86_64. Version code 4.

## Usage
- Open a title, select the language, episode and release, then choose **Download selected release**.
- Open **Downloads** for progress, pause/resume, retry, removal and **Play offline** after completion.
- Videos are saved to app-specific storage, not exported to the public Downloads folder. Uninstalling removes offline data.
- Phone playback hides system bars via WindowInsetsController on Android 11+ and immersive-sticky flags on older versions. Native controls offer Fit/Fill, Options → Rotate screen, audio/subtitles and Lock controls. Touch the picture to reveal Unlock. Android Back still exits the player.
- TV offers remote-accessible Downloads and player controls; rotation is phone-only.

## Implementation and limits
- Media3 DownloadManager, DownloadHelper, DownloadService and a persistent SimpleCache handle progressive files, HLS and DASH. Adaptive playback applies the downloaded stream keys. Offline playback is cache-only, with no network fallback.
- Only available, unprotected, non-live media can be downloaded. External subtitle URLs are not downloaded. Embedded/adaptive tracks depend on the source and device. No DRM bypass or offline license acquisition is implemented.
- Resume uses the saved source URL; expired links may require removal and a fresh download. Keep the app open during preparation. Android's foreground-service limits, battery restrictions, network and storage can pause or fail downloads. Reopen Downloads and Resume; no guaranteed reboot scheduling.
- Android edge swipes can temporarily reveal system bars. Manufacturer navigation/accessibility behavior varies; immersive mode does not disable Home or Back.
- The native ContentGate verifies metadata/releases before resolution and download preparation. Stored approved metadata and blocked IDs are checked again before download work, library listing and offline playback. The prior explicit-content filter remains in place.
- Download HTTP requests re-scope sensitive headers on every redirect/segment and validate Range responses. Downloads use app-private request metadata; stream links and authorization headers are not sent to the UI.

## Verification
- Both builds compile; 42 local JVM tests per app pass (history, languages, header policy, content gate, download policy).
- Phone/TV browser fixtures verify download selection/payload, library, pause/resume, offline action, remove/clear confirmation, empty state, modal Back and no overflow. TV remote navigation tested with mocked native transport.
- Existing phone, TV remote, watch-history and explicit-content-filter browser regressions pass. The content-filter suite covers 118 shared policy cases per platform, including PC.
- Eight local-server transport checks exercise the actual HTTP source with minimal Android/Media3 interfaces stubbed: full/partial responses, ignored or invalid Range, short probe, credentials retained same-origin and stripped across redirect, and HTTP failure.
- UI screenshots inspected at 390×844 and 1280×720. These are fixtures, not native-player or real-download evidence.
- No physical Android device/emulator was available: native playback, completed MP4/HLS/DASH offline downloads, notification behavior and manufacturer-specific system-bar hiding still require on-device verification.
- APK signatures match the prior filtered v0.3 packages, allowing an in-place update without intentionally clearing history/favorites. Older differently signed builds may not be update-compatible.
