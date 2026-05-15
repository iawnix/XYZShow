#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required" >&2
  exit 1
fi
BUILD_TOOLS="${BUILD_TOOLS:-$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)}"
GRADLE_BIN="${GRADLE_BIN:-gradle}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

APK_OUT="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

"$GRADLE_BIN" assembleDebug
"$BUILD_TOOLS/aapt" dump badging "$APK_OUT" | sed -n '1,12p'
"$BUILD_TOOLS/apksigner" verify --verbose "$APK_OUT"
echo "$APK_OUT"
