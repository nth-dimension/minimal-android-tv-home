# Shared helpers for install.sh and restore.sh. Sourced, not run.
PKG="dev.minimal.tvhome"
ACTIVITY="$PKG/.MainActivity"
LISTENER="$PKG/$PKG.data.MediaListenerService"
ADMIN="$PKG/.data.SleepAdmin"
DREAM="$PKG/.screensaver.PhotoDream"
# Fire TV only: the accessibility service that opens Minimal Home when Home brings up Amazon's screen.
REDIRECT="$PKG/$PKG.data.HomeRedirectService"
AMAZON_HOME="com.amazon.tv.launcher/.ui.HomeActivity_vNext"

# Where the TV's previous screensaver is remembered, per device, so restore.sh can put it back.
screensaver_backup() {
  local serial
  serial="$("$ADB" get-serialno | tr -d '\r\n' | tr -c 'A-Za-z0-9._-' '_')"
  echo "$ROOT/backups/screensaver-before-$serial.txt"
}
STOCK_LAUNCHERS=(
  "com.google.android.apps.tv.launcherx"      # Google TV home
  "com.google.android.tungsten.setupwraith"   # Google TV setup; re-asserts the stock home
)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v adb >/dev/null 2>&1; then
  ADB="${ADB:-adb}"
else
  ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
fi

if [[ -z "${JAVA_HOME:-}" ]] && ! /usr/libexec/java_home >/dev/null 2>&1; then
  STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  [[ -d "$STUDIO_JBR" ]] && export JAVA_HOME="$STUDIO_JBR"
fi

die() { echo "error: $*" >&2; exit 1; }

require_device() {
  [[ -x "$(command -v "$ADB")" || -x "$ADB" ]] || die "adb not found. Set ADB=/path/to/adb."
  # A TV given as ip:port that isn't connected yet: connect, and if macOS blocks adb
  # ("No route to host" while the port is open), restarting the adb server usually clears it.
  if [[ "${ANDROID_SERIAL:-}" == *:* ]] && ! "$ADB" devices | awk '$2=="device"{print $1}' | grep -qx "$ANDROID_SERIAL"; then
    if ! "$ADB" connect "$ANDROID_SERIAL" | grep -q "connected to"; then
      "$ADB" kill-server >/dev/null 2>&1 || true
      "$ADB" connect "$ANDROID_SERIAL" | grep -q "connected to" \
        || die "can't reach $ANDROID_SERIAL. Is the TV on, with network debugging on? On macOS, also allow your terminal under System Settings > Privacy & Security > Local Network."
    fi
  fi
  local count
  count=$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  if [[ -z "${ANDROID_SERIAL:-}" && "$count" != "1" ]]; then
    "$ADB" devices
    die "Expected exactly one connected device, found $count. Connect with 'adb connect <tv-ip>' or set ANDROID_SERIAL."
  fi
  echo "Device: $("$ADB" shell getprop ro.product.model | tr -d '\r') (Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r'))"
}

# Which activity the Home button opens now, e.g. dev.minimal.tvhome/.MainActivity.
home_activity() {
  "$ADB" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | tr -d '\r' | tail -1 | tr -d ' '
}

# On Google TV, a Wi-Fi network saved during first setup belongs to the setup wizard (setupwraith),
# and Android deletes it when the wizard is disabled, leaving the TV offline.
wifi_owned_by_setup() {
  local dump
  dump="$("$ADB" shell dumpsys wifi 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$dump" ]]; then
    # Fail safe: a dropped adb connection must not look like "not owned".
    echo "  note: couldn't read the Wi-Fi state; treating the network as the setup wizard's."
    return 0
  fi
  grep -q 'cname=[^ ]*setupwraith' <<<"$dump"
}

is_installed() { "$ADB" shell pm list packages "$1" | tr -d '\r' | grep -qx "package:$1"; }

# Fire OS declares this feature on every Fire TV. (Vega OS sticks aren't Android and never get here.)
is_fire_tv() { "$ADB" shell pm list features | tr -d '\r' | grep -qx "feature:amazon.hardware.fire_tv"; }

# The accessibility services the TV has on, as a colon-separated list ("null" when unset).
accessibility_services() {
  local list
  list="$("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
  [[ "$list" == "null" ]] && list=""
  echo "$list"
}

# The package in front right now, e.g. dev.minimal.tvhome. Empty if it can't be read.
foreground_package() {
  "$ADB" shell dumpsys activity activities 2>/dev/null | tr -d '\r' \
    | grep -oE '(mResumedActivity|topResumedActivity|ResumedActivity)[^}]*' | head -1 \
    | grep -oE '[A-Za-z0-9_.]+/' | head -1 | tr -d '/'
}
