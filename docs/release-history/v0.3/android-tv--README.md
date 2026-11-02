# Yoru filtered Android build — created by Obscura

This version adds an always-on explicit-content filter, including an offline
explicit-anime alias index and native metadata/playback checks. There is no disable
switch. Known explicit labels/titles and matching release filenames are blocked;
search/discovery entries whose metadata cannot be checked are omitted. The browser
UI adds an additional layer, but the actual gate runs in native Android Java.
No new runtime classification service receives your queries. Ordinary anime is not
blanket-blocked; Hunter x Hunter and other non-explicit title-collision regressions
are covered by the shared tests. This is not a guarantee against mislabeling or a
parental-control certification. See safety/README.md and the database license files.

INSTALLATION / SIGNING NOTE
The attached APK is debug-signed, versionCode 3, with the same application ID as the
previous Yoru preview. If its signing certificate differs from your installed
preview, Android will not accept it as an update. Uninstall the old preview first
before installing this filtered build. Uninstalling deletes that app's saved local
favorites, settings and history; record anything you want to keep first. Do not
bypass Android's security checks. These are not Play Store production releases.

Android TV / Google TV preview application. Android 8.0+; ARM64, ARMv7 and x86_64.
This is not an iOS, Samsung Tizen, LG webOS, Roku or Fire TV certification build.
Media plays in the built-in AndroidX Media3 / ExoPlayer player. No external VLC/mpv
installation is required. Availability and stream seek support depend on providers.

## Included in this update

- Yoru name, Y launcher identity, and About Yoru & Credits with Obscura attribution.
- Five configured providers: MovieBox, 4KHDHub, Dramachi, CircleFTP and DhakaFlix.
  HubCloud/Pixeldrain mirror support and upstream/runtime license acknowledgements.
- Local Continue watching and full history, saved Hindi/other audio edition,
  season/episode and release when available; Resume and Start over.
- Player-reported progress saved about every 3 seconds while ready, and on pause,
  Back/stop or lifecycle exit. Completion at 95% or an ended playback event.
- Remove/confirmed clear; clearing preserves favorites and stops deleted active
  sessions from repopulating history. At most 200 history entries are retained.
- D-pad navigation, visible focus, Back support and scrollable credits.

## Installing the supplied preview APK

Download Yoru-TV-filtered.apk and open it on your Android TV / Google TV.
Follow the device's normal APK installation permission prompts and security policy.
Do not disable Play Protect or bypass device-management restrictions.

This is a debug-signed preview, not a store release. Package ID is
`com.obscura.yoru.tv`. It installs beside the old MovieBox preview rather than
replacing it, so no uninstall or data loss is required. The old app's favorites and
settings are not automatically imported. Phone, TV and desktop history do not sync.
For future production updates, the publisher must use a stable private signing key;
GitHub's temporary debug keys do not guarantee in-place updates to this APK.

## Source overlay / rebuild

Merge this folder, the included src/config.rs and workflows into the existing full
MovieBox-TUI project. Do not replace the full project with this small overlay or
delete existing Cargo/source files. Internal JNI namespace/provider identifiers
remain unchanged for compatibility; the visible app is Yoru.

Workflow: `.github/workflows/yoru-android-tv.yml`.
A visible identical copy is `android-tv/build-android-tv.yml`.
Open Actions → Build Yoru Android TV → Run workflow and download the artifact.
No source upload is needed just to install the attached APK.

Local source build requires JDK 17, Gradle 8.10.2, Android SDK 35/build-tools 35.0.0,
NDK 27.2.12479018, Rust Android targets and cargo-ndk 4.1.2. Run
`bash android-tv/build-android.sh` from the full repository.
The script compiles the provider engine from the included native source. The APKs
built in this session reuse the byte-identical provider libraries from the earlier
preview APKs, since that engine did not change; Java and bundled UI were rebuilt.

## Validation and limits

32 Java tests passed for this app (15 content-gate tests, 12 history/storage tests plus 5 audio/header
policy tests). Touch/remote browser regression suites and the new Android history
suite passed. APK signature, labels/IDs, three ABI libraries, and 16-KiB ZIP alignment
were checked. The new APK's assets were compared against the updated source.
See VALIDATION.json for artifact hashes and machine-readable checks.

Real installation, Media3 playback, codec support, Android lifecycle/resume behavior,
actual streams, subtitles and network conditions still require device testing.
No movie was played to create the previews. Screenshots render the actual bundled
WebView layout in Chromium using an earlier provider catalog/artwork snapshot and
synthetic progress/API responses. They are UI previews, not on-device screenshots
or guarantees of title/audio availability. No test history is bundled in the APK.

Progress is approximate to a few seconds, not frame-exact. Seeking can fail on live,
unseekable or changed releases. Links are refreshed when reopening; authenticated
stream URLs and headers are not stored in history. BDIX sources require compatible
networks. Favorites/settings/history are in this app's private local storage;
clearing app data or uninstalling removes them. Progress before this update does
not exist. Use only content you are authorized to access.

For GitHub web upload, upload the source folders separately; the combined Android
overlay exceeds the web uploader's 100-file limit. APK installation does not need
GitHub or any source upload.
