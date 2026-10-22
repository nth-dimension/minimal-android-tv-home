#!/usr/bin/env bash
# Re-enables the stock Google TV launcher. Minimal Home stays installed unless --uninstall is given.
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
    -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
    *) die "unknown option: $arg" ;;
  esac
done

require_device

enabled=0
for p in "${STOCK_LAUNCHERS[@]}"; do
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
