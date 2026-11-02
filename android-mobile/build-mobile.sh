#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
: "${ANDROID_HOME:?Install the Android SDK and set ANDROID_HOME first}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.2.12479018}"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export CARGO_BUILD_JOBS="${CARGO_BUILD_JOBS:-3}"
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"
command -v cargo >/dev/null
command -v gradle >/dev/null
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -P 26 \
  -o android-mobile/app/src/main/jniLibs build --release --locked \
  --manifest-path android-mobile/native/Cargo.toml
gradle --no-daemon -p android-mobile :app:testDebugUnitTest :app:assembleDebug
mkdir -p android-mobile/dist
cp android-mobile/app/build/outputs/apk/debug/app-debug.apk android-mobile/dist/Yoru-Mobile-filtered.apk
printf '\nAPK ready: android-mobile/dist/Yoru-Mobile-filtered.apk\n'
