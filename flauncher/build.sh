#!/usr/bin/env bash
# Rebuilds the FLauncher APK for 32-bit Android TV boxes: upstream FLauncher at a
# pinned commit plus flauncher.patch (no Firebase, Unsplash via a build-time key,
# Menu key opens the app menu, highlight animation off by default).
#
# Usage: flauncher/build.sh [--fetch-toolchain] [--no-unsplash] [--skip-tests] [--app-id ID]
#   --fetch-toolchain  download Flutter 3.7.5, Amazon Corretto 17 and Gradle 7.4 into the work dir
#   --no-unsplash      build without the Unsplash key (the Unsplash wallpaper option is then hidden)
#   --skip-tests       skip flutter analyze and flutter test
#   --app-id ID        Android package name (default com.zeasn.launcher, a name the Hikeen firmware
#                      treats as its launcher; use me.efesser.flauncher for upstream's)
#
# Without --fetch-toolchain, set FLUTTER_ROOT (Flutter 3.7.5) and JAVA_HOME (JDK 17).
# Optional: GRADLE_ZIP (a local gradle-7.4-all.zip, checked against its SHA-256),
# ANDROID_HOME (default ~/Library/Android/sdk), FLAUNCHER_WORK (default build/flauncher).
# The Unsplash key is read from unsplash.accessKey in local.properties.
set -euo pipefail

UPSTREAM=https://gitlab.com/etienn01/flauncher.git
UPSTREAM_COMMIT=4930cd738a5ab32967953fe8b12790bc4b452872
FLUTTER_VERSION=3.7.5
GRADLE_SHA256=cd5c2958a107ee7f0722004a12d0f8559b4564c34daad7df06cffd4d12a426d0
GRADLE_URLS=(
  https://services.gradle.org/distributions/gradle-7.4-all.zip
  https://mirrors.cloud.tencent.com/gradle/gradle-7.4-all.zip
)

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(dirname "$HERE")"
WORK="${FLAUNCHER_WORK:-$REPO/build/flauncher}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

fetch=0 unsplash=1 tests=1 app_id=com.zeasn.launcher
while [ $# -gt 0 ]; do
  arg=$1; shift
  case "$arg" in
    --fetch-toolchain) fetch=1 ;;
    --no-unsplash) unsplash=0 ;;
    --skip-tests) tests=0 ;;
    --app-id) [ $# -gt 0 ] || { echo "--app-id needs a value" >&2; exit 2; }; app_id=$1; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

[[ "$app_id" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]] || { echo "bad --app-id: $app_id" >&2; exit 2; }

say() { printf '\n== %s\n' "$*"; }
die() { echo "error: $*" >&2; exit 1; }
sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
download() { curl -fL --retry 3 --retry-all-errors -o "$2" "$1"; }

mkdir -p "$WORK"
TOOLS="$WORK/toolchain"

if [ "$fetch" = 1 ]; then
  mkdir -p "$TOOLS"
  os=$(uname -s) arch=$(uname -m)
  case "$os/$arch" in
    Darwin/arm64)  flutter_file="flutter_macos_arm64_${FLUTTER_VERSION}-stable.zip"; flutter_json=releases_macos.json; jdk=amazon-corretto-17-aarch64-macos-jdk.tar.gz ;;
    Darwin/x86_64) flutter_file="flutter_macos_${FLUTTER_VERSION}-stable.zip";       flutter_json=releases_macos.json; jdk=amazon-corretto-17-x64-macos-jdk.tar.gz ;;
    Linux/x86_64)  flutter_file="flutter_linux_${FLUTTER_VERSION}-stable.tar.xz";     flutter_json=releases_linux.json; jdk=amazon-corretto-17-x64-linux-jdk.tar.gz ;;
    *) die "no toolchain download for $os/$arch; set FLUTTER_ROOT and JAVA_HOME yourself" ;;
  esac
  infra=https://storage.googleapis.com/flutter_infra_release/releases

  if [ ! -x "$TOOLS/flutter/bin/flutter" ]; then
    say "Downloading Flutter $FLUTTER_VERSION"
    os_dir=$( [ "$os" = Darwin ] && echo macos || echo linux )
    download "$infra/stable/$os_dir/$flutter_file" "$TOOLS/$flutter_file"
    want=$(curl -fsSL "$infra/$flutter_json" | python3 -I -c '
import json, sys
name = sys.argv[1]
for r in json.load(sys.stdin)["releases"]:
    if r["archive"].endswith("/" + name):
        print(r["sha256"]); break
' "$flutter_file")
    [ -n "$want" ] && [ "$(sha256 "$TOOLS/$flutter_file")" = "$want" ] || die "Flutter archive checksum mismatch"
    case "$flutter_file" in
      *.zip) unzip -q "$TOOLS/$flutter_file" -d "$TOOLS" ;;
      *) tar -xJf "$TOOLS/$flutter_file" -C "$TOOLS" ;;
    esac
    rm -f "$TOOLS/$flutter_file"
  fi

  if [ ! -d "$TOOLS/jdk17" ]; then
    say "Downloading Amazon Corretto 17"
    download "https://corretto.aws/downloads/latest/$jdk" "$TOOLS/$jdk"
    want=$(curl -fsSL "https://corretto.aws/downloads/latest_sha256/$jdk")
    [ "$(sha256 "$TOOLS/$jdk")" = "$want" ] || die "JDK checksum mismatch"
    mkdir -p "$TOOLS/jdk17" && tar -xzf "$TOOLS/$jdk" -C "$TOOLS/jdk17" --strip-components=1 && rm -f "$TOOLS/$jdk"
  fi

  if [ ! -f "$TOOLS/gradle-7.4-all.zip" ]; then
    say "Downloading Gradle 7.4"
    for url in "${GRADLE_URLS[@]}"; do
      if download "$url" "$TOOLS/gradle-7.4-all.zip.part" && [ "$(sha256 "$TOOLS/gradle-7.4-all.zip.part")" = "$GRADLE_SHA256" ]; then
        mv "$TOOLS/gradle-7.4-all.zip.part" "$TOOLS/gradle-7.4-all.zip"; break
      fi
      rm -f "$TOOLS/gradle-7.4-all.zip.part"
    done
    [ -f "$TOOLS/gradle-7.4-all.zip" ] || die "could not download Gradle 7.4 with the expected checksum"
  fi

  FLUTTER_ROOT="$TOOLS/flutter"
  if [ -d "$TOOLS/jdk17/Contents/Home" ]; then JAVA_HOME="$TOOLS/jdk17/Contents/Home"; else JAVA_HOME="$TOOLS/jdk17"; fi
  GRADLE_ZIP="${GRADLE_ZIP:-$TOOLS/gradle-7.4-all.zip}"
fi

[ -n "${FLUTTER_ROOT:-}" ] && [ -x "$FLUTTER_ROOT/bin/flutter" ] || die "set FLUTTER_ROOT to a Flutter $FLUTTER_VERSION SDK, or use --fetch-toolchain"
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] || die "set JAVA_HOME to a JDK 17, or use --fetch-toolchain"
java_version=$("$JAVA_HOME/bin/java" -version 2>&1 || true)
grep -q 'version "17\.' <<<"$java_version" || die "JAVA_HOME must be a JDK 17 (Gradle 7.4 does not run on newer ones)"
[ -d "$ANDROID_HOME" ] || die "Android SDK not found at $ANDROID_HOME (set ANDROID_HOME)"
export FLUTTER_ROOT JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$FLUTTER_ROOT/bin:$PATH" FLUTTER_SUPPRESS_ANALYTICS=true CI=true
export GRADLE_USER_HOME="$WORK/gradle-home"

flutter_version=$(flutter --version 2>/dev/null || true)
grep -q "^Flutter $FLUTTER_VERSION " <<<"$flutter_version" || die "FLUTTER_ROOT is not Flutter $FLUTTER_VERSION"

if [ ! -d "$ANDROID_HOME/platforms/android-33" ] || [ ! -d "$ANDROID_HOME/build-tools/33.0.2" ]; then
  say "Installing Android SDK platform 33 and build-tools 33.0.2"
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-33" "build-tools;33.0.2" >/dev/null
fi

say "Fetching FLauncher $UPSTREAM_COMMIT"
SRC="$WORK/src"
rm -rf "$SRC"
git clone -q "$UPSTREAM" "$SRC"
git -C "$SRC" -c advice.detachedHead=false checkout -q "$UPSTREAM_COMMIT"
git -C "$SRC" apply --whitespace=nowarn "$HERE/flauncher.patch"

if [ -n "${GRADLE_ZIP:-}" ]; then
  [ "$(sha256 "$GRADLE_ZIP")" = "$GRADLE_SHA256" ] || die "GRADLE_ZIP is not the expected gradle-7.4-all.zip"
  # A file: URI (spaces escaped), with the colon escaped for the .properties format.
  gradle_uri=$(python3 -I -c 'import pathlib, sys; print(pathlib.Path(sys.argv[1]).resolve().as_uri())' "$GRADLE_ZIP")
  python3 -I - "$SRC/android/gradle/wrapper/gradle-wrapper.properties" "${gradle_uri/:/\\:}" <<'PY'
import re, sys, pathlib
p = pathlib.Path(sys.argv[1])
s, n = re.subn(r"(?m)^distributionUrl=.*$", lambda m: "distributionUrl=" + sys.argv[2], p.read_text())
assert n == 1
p.write_text(s)
PY
fi

cd "$SRC"
flutter pub get >/dev/null

if [ "$tests" = 1 ]; then
  say "flutter analyze"
  flutter analyze
  say "flutter test"
  flutter test
fi

define=()
if [ "$unsplash" = 1 ]; then
  key=$(grep -E '^unsplash\.accessKey=' "$REPO/local.properties" 2>/dev/null | cut -d= -f2- || true)
  if [ -n "$key" ]; then define=(--dart-define="UNSPLASH_ACCESS_KEY=$key"); else echo "No unsplash.accessKey in local.properties: building without Unsplash."; fi
fi

say "Building release APK (armeabi-v7a, $app_id)"
FLAUNCHER_APPLICATION_ID=$app_id flutter build apk --release --target-platform android-arm ${define[@]+"${define[@]}"}

OUT="$WORK/flauncher-arm32-$app_id.apk"
cp build/app/outputs/flutter-apk/app-release.apk "$OUT"
say "Done"
echo "$OUT"
echo "sha256 $(sha256 "$OUT")"
echo "The APK contains the Unsplash key if one was used: keep it out of git."
