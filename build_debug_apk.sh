#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN_ROOT="${LOCAL_TOOLCHAIN_ROOT:-$HOME/soft}"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$TOOLCHAIN_ROOT/android/sdk}}"
if [[ -z "$SDK_DIR" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required" >&2
  exit 1
fi
BUILD_TOOLS="${BUILD_TOOLS:-$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)}"
GRADLE_BIN="${GRADLE_BIN:-$TOOLCHAIN_ROOT/gradle/gradle-8.9/bin/gradle}"
export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN_ROOT/jdk21-local/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-home}"

APK_OUT="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

"$GRADLE_BIN" --no-daemon ${GRADLE_ARGS:-} assembleDebug
"$BUILD_TOOLS/aapt" dump badging "$APK_OUT" | sed -n '1,12p'
"$BUILD_TOOLS/apksigner" verify --verbose "$APK_OUT"
echo "$APK_OUT"
