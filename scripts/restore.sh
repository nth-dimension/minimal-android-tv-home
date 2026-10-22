#!/usr/bin/env bash
# Gives Home back to the TV's own launcher: on Google TV by re-enabling it, on Fire TV by turning
# the Home redirect off. Minimal Home stays installed unless --uninstall is given.
#
#   scripts/restore.sh               re-enable stock launcher and the TV's own screensaver
#   scripts/restore.sh --uninstall   also remove Minimal Home
#   scripts/restore.sh --reboot      reboot afterwards (recommended if casting misbehaves)
set -euo pipefail
source "$(dirname "$0")/common.sh"

UNINSTALL=0
REBOOT=0
for arg in "$@"; do
  case "$arg" in
    --uninstall) UNINSTALL=1 ;;
    --reboot) REBOOT=1 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) die "unknown option: $arg" ;;
  esac
done

require_device

if is_fire_tv; then
  # Amazon's launcher was never disabled; Home goes back to it once our redirect service is off.
  # First take back the permission Minimal Home uses to turn the service on again by itself, or it
  # would do so the next time it opened.
  if is_installed "$PKG"; then
    "$ADB" shell pm revoke "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || true
  fi
  services="$(accessibility_services)"
  if tr ':' '\n' <<<"$services" | grep -qx "$REDIRECT"; then
    # grep finds nothing when ours is the only service; that must not end the script.
    remaining="$(tr ':' '\n' <<<"$services" | { grep -vx "$REDIRECT" || true; } | paste -sd ':' -)"
    "$ADB" shell settings put secure enabled_accessibility_services "'$remaining'"
    # Fire OS treats accessibility as one switch; some apps (Prime Video) behave differently while it's on.
    [[ -z "$remaining" ]] && "$ADB" shell settings put secure accessibility_enabled 0
    echo "Home redirect service off; Home opens Amazon's home screen again."
  else
    echo "Home redirect service was already off."
  fi
  "$ADB" shell am start -n "$AMAZON_HOME" >/dev/null 2>&1 || true
fi

enabled=0
is_fire_tv && enabled=1   # nothing to re-enable; skip the Google TV launcher loop below
for p in "${STOCK_LAUNCHERS[@]}"; do
  is_fire_tv && break
  # A launcher another tool removed for this user has to be put back before it can be enabled.
  if ! is_installed "$p" && "$ADB" shell pm list packages -u | tr -d '\r' | grep -qx "package:$p"; then
    "$ADB" shell cmd package install-existing --user 0 "$p" >/dev/null 2>&1 || true
  fi
  is_installed "$p" || continue
  if "$ADB" shell pm enable --user 0 "$p" | tr -d '\r' | grep -q "new state: enabled"; then
    echo "Enabled $p"
    enabled=$((enabled + 1))
  else
    echo "  warning: could not enable $p"
  fi
done
[[ $enabled -gt 0 ]] || die "No stock launcher could be enabled, so Minimal Home stays installed and in place."

# Android drops the Home preference whenever the set of Home apps changes; point it at the stock home.
stock="$("$ADB" shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | tr -d '\r' | grep -oE 'com\.google\.android\.(apps\.tv\.launcherx|tvlauncher)/[^ ]+' | head -1 || true)"
if [[ -n "$stock" ]]; then
  "$ADB" shell cmd package set-home-activity --user 0 "$stock" >/dev/null 2>&1 || true
fi

# Put the TV's own screensaver back if ours is set.
if [[ "$("$ADB" shell settings get secure screensaver_components | tr -d '\r')" == "$DREAM" ]]; then
  backup="$(screensaver_backup)"
  previous=""
  [[ -f "$backup" ]] && previous="$(cat "$backup")"
  [[ -z "$previous" || "$previous" == "null" ]] && \
    previous="$("$ADB" shell settings get secure screensaver_default_component | tr -d '\r')"
  if [[ -n "$previous" && "$previous" != "null" ]]; then
    "$ADB" shell settings put secure screensaver_components "$previous" && echo "Screensaver back to $previous"
  else
    "$ADB" shell settings delete secure screensaver_components >/dev/null && echo "Screensaver back to the TV's default"
  fi
fi

if [[ $UNINSTALL == 1 ]] && is_installed "$PKG"; then
  # An active device admin can't be uninstalled; the app drops its own admin on this adb-only command.
  "$ADB" shell am broadcast -a dev.minimal.tvhome.REMOVE_ADMIN -n "$PKG/.data.SleepTimerCommands" >/dev/null 2>&1 || true
  "$ADB" uninstall "$PKG" >/dev/null && echo "Uninstalled $PKG"
fi

if [[ $REBOOT == 1 ]]; then
  echo "Rebooting..."
  "$ADB" reboot
else
  echo "Done. Reboot the TV if Home still opens Minimal Home or casting misbehaves."
fi
