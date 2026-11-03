# Yoru Android Mobile — public release

The public mobile release is versionCode 6 / 0.5.0. Debug builds use the separate application ID `com.obscura.yoru.mobile.test`; release builds use `com.obscura.yoru.mobile` and require the private release keystore configured in GitHub Actions.

# Yoru Android Mobile — created by Obscura

Current Android source: versionCode 6, `0.5.0`. Android 8+; ARM64, ARMv7 and x86_64.

This full cumulative source includes Yoru branding, Obscura/provider/upstream credits, favorites/settings, language and quality selection, history/Continue watching, the always-on explicit-content filter, and offline downloads. Phone playback also adds immersive system-bar hiding and rotation. Fit/Fill and control lock are included in both apps.

See the repository README.md and ANDROID_OFFLINE_FULLSCREEN.md for features, limits, signing and verification. Read safety/README.md for filter limitations. Downloads are private app data; external subtitle URLs, live and protected downloads are not supported. No actual media is bundled.

## Rebuild
Workflow: `.github/workflows/yoru-android-mobile.yml`. A visible identical workflow copy is in this folder. GitHub Actions installs the toolchains and compiles the JNI backend from the included source. Local builds require JDK 17, Gradle 8.10.2, Android SDK 35/build-tools 35.0.0, NDK 27.2.12479018, the Rust Android targets and cargo-ndk 4.1.2.

The manual workflow produces a debug-signed test APK. The main Release workflow produces a non-debuggable production APK using the repository owner's private signing key; the key is supplied through GitHub Actions secrets and is never committed.

Package ID: `com.obscura.yoru.mobile`. Release updates can be installed in place only when they use the same release keystore. Earlier differently signed builds may require an uninstall, which removes history, favorites, settings and downloads.

## Configure public release signing

Create one keystore and keep it backed up privately; every future Android update must use the same key:

```powershell
keytool -genkeypair -v -storetype JKS -keystore yoru-release.jks -alias yoru-release -keyalg RSA -keysize 2048 -validity 10000
```

In the repository's GitHub settings, add these Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The following PowerShell commands can upload them without putting the keystore in Git:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("yoru-release.jks")) | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS --body "yoru-release"
gh secret set ANDROID_KEY_PASSWORD
```

After the version commit is pushed, the `Release` workflow builds the signed APK and attaches it to the GitHub Release.

42 local JVM tests and browser fixture checks passed. APK signature, version/ID, three ABI libraries, 16-KiB ZIP alignment and current bundled asset bytes were verified. Native playback/downloads and device-specific immersive behavior still require physical-device testing. See VALIDATION.json.
