#!/usr/bin/env bash
# Builds Minimal Home, installs it on the connected TV, grants its permissions,
# and makes it the Home screen by disabling the stock Google TV launcher.
#
#   scripts/install.sh                      build, install, grant, take over Home
#   scripts/install.sh --skip-build         reuse the last built APK
#   scripts/install.sh --keep-stock-launcher  install and grant only; Home stays stock
#   scripts/install.sh --unsplash-key KEY   save your Unsplash access key, then build and install
#   scripts/install.sh --keep-screensaver   leave the TV's screensaver as it is
#
# Undo everything with scripts/restore.sh.
set -euo pipefail
source "$(dirname "$0")/common.sh"

BUILD=1
TAKEOVER=1
SCREENSAVER=1
UNSPLASH_KEY=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) BUILD=0 ;;
    --keep-stock-launcher) TAKEOVER=0 ;;
    --keep-screensaver) SCREENSAVER=0 ;;
    --unsplash-key)
      [[ $# -ge 2 && -n "$2" ]] || die "--unsplash-key needs a value"
      UNSPLASH_KEY="$2"; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done

if [[ -n "$UNSPLASH_KEY" ]]; then
  [[ "$UNSPLASH_KEY" =~ ^[A-Za-z0-9_-]+$ ]] || die "That doesn't look like an Unsplash access key."
  PROPS="$ROOT/local.properties"
  touch "$PROPS"
  grep -v '^unsplash.accessKey=' "$PROPS" > "$PROPS.tmp" || true
  echo "unsplash.accessKey=$UNSPLASH_KEY" >> "$PROPS.tmp"
  mv "$PROPS.tmp" "$PROPS"
  echo "Saved Unsplash access key to local.properties (not committed to git)."
  [[ $BUILD == 1 ]] || die "--unsplash-key needs a build; drop --skip-build."
fi

require_device

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [[ $BUILD == 1 ]]; then
  echo "Building release APK..."
  (cd "$ROOT" && ./gradlew --quiet :app:assembleRelease)
fi
[[ -f "$APK" ]] || die "APK not found at $APK. Run without --skip-build."

echo "Installing..."
"$ADB" install -r "$APK" >/dev/null

echo "Granting permissions..."
"$ADB" shell pm grant "$PKG" android.permission.READ_TV_LISTINGS \
  || echo "  warning: could not grant READ_TV_LISTINGS; the app will ask on first launch."
"$ADB" shell appops set "$PKG" GET_USAGE_STATS allow
"$ADB" shell cmd notification allow_listener "$LISTENER" \
  || echo "  warning: could not grant playback access; Up Next will only show apps' own rows."
# Sleep timer: lets the app put the TV into standby (lock screen policy only), and on
# Android 13+ show its "turns off in 1 minute" warning over other apps.
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
"$ADB" shell dpm set-active-admin --user 0 "$ADMIN" >/dev/null 2>&1 || true
if "$ADB" shell dumpsys device_policy | tr -d '\r' | grep -q "$PKG/.data.SleepAdmin\|$PKG/$PKG.data.SleepAdmin"; then
  echo "  sleep timer can turn the TV off"
else
  echo "  warning: could not make Minimal Home a device admin; the sleep timer can't turn the TV off."
fi

if [[ $SCREENSAVER == 1 ]]; then
  echo "Setting the screensaver..."
  before="$("$ADB" shell settings get secure screensaver_components | tr -d '\r')"
  if [[ "$before" != "$DREAM" ]]; then
    mkdir -p "$ROOT/backups"
    echo "$before" > "$(screensaver_backup)"
  fi
  "$ADB" shell settings put secure screensaver_components "$DREAM"
  "$ADB" shell settings put secure screensaver_enabled 1
  if [[ "$("$ADB" shell settings get secure screensaver_components | tr -d '\r')" == "$DREAM" ]]; then
    if [[ "$before" == "$DREAM" ]]; then echo "  screensaver: Unsplash photos"
    else echo "  screensaver: Unsplash photos (was ${before:-none})"; fi
  else
    echo "  warning: could not set the screensaver."
  fi
fi

if [[ $TAKEOVER == 1 ]]; then
  echo "Taking over Home..."
  # Minimal Home must be running before the stock home goes, or Home can land on a factory test app.
  "$ADB" shell am start -n "$ACTIVITY" >/dev/null
  for p in "${STOCK_LAUNCHERS[@]}"; do
    is_installed "$p" || continue
    if [[ "$p" == "com.google.android.tungsten.setupwraith" ]] && wifi_owned_by_setup; then
      echo "  kept $p: it owns the saved Wi-Fi network, and disabling it would delete that network."
      echo "  Forget and rejoin the Wi-Fi network in Settings, then run this again."
      continue
    fi
    "$ADB" shell pm disable-user --user 0 "$p" >/dev/null && echo "  disabled $p"
  done
  # Set last: Android drops the Home preference whenever the set of Home apps changes.
  "$ADB" shell cmd package set-home-activity --user 0 "$ACTIVITY" >/dev/null 2>&1 || true
  home="$(home_activity)"
  if [[ "$home" != "$ACTIVITY" && "$home" != "$PKG/$PKG.MainActivity" ]]; then
    echo "  Home still opens ${home:-nothing}; putting the stock launcher back."
    for p in "${STOCK_LAUNCHERS[@]}"; do
      is_installed "$p" || continue
      "$ADB" shell pm enable --user 0 "$p" >/dev/null || echo "  warning: could not re-enable $p"
    done
    die "Minimal Home did not take over Home. The stock home is back; see README > Known risks."
  fi
  echo "  Home opens Minimal Home."
fi

"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo
echo "Done. Press Home on the remote to check Minimal Home opens."
[[ $TAKEOVER == 1 ]] && echo "To undo: scripts/restore.sh"
exit 0
