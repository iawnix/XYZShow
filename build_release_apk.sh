#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN_ROOT="${LOCAL_TOOLCHAIN_ROOT:-$HOME/soft}"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$TOOLCHAIN_ROOT/android/sdk}}"
GRADLE_BIN="${GRADLE_BIN:-$TOOLCHAIN_ROOT/gradle/gradle-8.9/bin/gradle}"
BUILD_TOOLS="${BUILD_TOOLS:-$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)}"
SIGNING_DIR="${SIGNING_DIR:-$ROOT_DIR/local-signing}"
KEYSTORE="${KEYSTORE:-$SIGNING_DIR/xyzshow-release.jks}"
SIGNING_ENV="${SIGNING_ENV:-$SIGNING_DIR/release.env}"
KEY_ALIAS="${KEY_ALIAS:-xyzshow-release}"

export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN_ROOT/jdk21-local/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-home}"

if [[ ! -d "$SDK_DIR" ]]; then
  echo "Android SDK not found: $SDK_DIR" >&2
  exit 1
fi
if [[ ! -x "$GRADLE_BIN" ]]; then
  echo "Gradle not found or not executable: $GRADLE_BIN" >&2
  exit 1
fi

mkdir -p "$SIGNING_DIR"
chmod 700 "$SIGNING_DIR"

if [[ -f "$SIGNING_ENV" ]]; then
  # shellcheck disable=SC1090
  source "$SIGNING_ENV"
fi

if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
  umask 077
  KEYSTORE_PASSWORD="$(openssl rand -hex 24)"
  KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
  {
    printf 'export KEYSTORE_PASSWORD=%q\n' "$KEYSTORE_PASSWORD"
    printf 'export KEY_ALIAS=%q\n' "$KEY_ALIAS"
    printf 'export KEY_PASSWORD=%q\n' "$KEY_PASSWORD"
  } > "$SIGNING_ENV"
  chmod 600 "$SIGNING_ENV"
fi
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=XYZShow Release,O=iawnix,C=CN"
  chmod 600 "$KEYSTORE"
fi

UNSIGNED="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
RELEASE_DIR="$ROOT_DIR/releases"
mkdir -p "$RELEASE_DIR"

"$GRADLE_BIN" --no-daemon ${GRADLE_ARGS:-} assembleRelease

VERSION_NAME="$("$BUILD_TOOLS/aapt" dump badging "$UNSIGNED" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -n 1)"
OUT_APK="$RELEASE_DIR/XYZShow-release-${VERSION_NAME:-unknown}.apk"
rm -f "$OUT_APK"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" \
  --key-pass "pass:$KEY_PASSWORD" \
  --v4-signing-enabled false \
  --out "$OUT_APK" \
  "$UNSIGNED"

"$BUILD_TOOLS/aapt" dump badging "$OUT_APK" | sed -n '1,12p'
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_APK"
sha256sum "$OUT_APK"
echo "$OUT_APK"
