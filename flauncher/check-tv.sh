#!/usr/bin/env bash
# Health check for the generic Hikeen/HiSilicon Android TV 14 box running FLauncher.
#
# Usage: flauncher/check-tv.sh [SERIAL] [--launch] [--reboot] [--standby]
#   SERIAL     adb serial (default 192.168.0.103:5555; that port survives Wi-Fi drops)
#   --launch   also drive the UI: open apps and return Home, open the Menu-key panel, open tiles
#   --reboot   reboot the TV first and check that everything comes back. adb stays off after a
#              reboot until Wireless debugging is turned on again on the TV; the script waits 10 min
#   --standby  put the TV in standby and wake it (wake it with the remote if adb loses it)
#
# Read-only unless one of the options is given. Exits non-zero if any check fails.
set -uo pipefail

SERIAL=192.168.0.103:5555 launch=0 reboot=0 standby=0
for arg in "$@"; do
  case "$arg" in
    --launch) launch=1 ;;
    --reboot) reboot=1 ;;
    --standby) standby=1 ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) SERIAL=$arg ;;
  esac
done

HERE="$(cd "$(dirname "$0")" && pwd)"
APPLIED="${APPLIED:-$(dirname "$HERE")/debloat/generic-androidtv14-hikeen-applied.txt}"
ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
FL_PKG=${FL_PKG:-com.zeasn.launcher}   # FLauncher, built with --app-id com.zeasn.launcher
FL_HOME=$FL_PKG/me.efesser.flauncher.MainActivity
# Must stay enabled: store, Play services, the apps in use, settings, TV provider, phone remote, DLNA.
KEEP=(com.android.vending com.google.android.gms com.google.android.youtube.tv in.startv.hotstar
      com.android.tv.settings com.android.providers.tv com.google.android.tv.remote.service com.hisilicon.dlna.dmr)

pass=0 fail=0
ok()   { pass=$((pass+1)); printf '  PASS  %s\n' "$*"; }
bad()  { fail=$((fail+1)); printf '  FAIL  %s\n' "$*"; }
note() { printf '  info  %s\n' "$*"; }
a() { "$ADB" -s "$SERIAL" "$@"; }
sh_() { a shell "$@" 2>/dev/null | tr -d '\r'; }
top_activity() { sh_ dumpsys activity activities | grep -m1 topResumedActivity | sed -E 's/.*u0 ([^ ]+).*/\1/'; }
now_ms() { python3 -I -c 'import time; print(int(time.time() * 1000))'; }
wait_top() { # wait_top PREFIX TIMEOUT_S -> prints ms waited, or TIMEOUT
  local start; start=$(now_ms)
  local deadline=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -le "$deadline" ]; do
    case "$(top_activity)" in "$1"*) echo $(( $(now_ms) - start )); return 0 ;; esac
    sleep 0.3
  done
  echo TIMEOUT; return 1
}
key() { a shell input keyevent "KEYCODE_$1" >/dev/null 2>&1; sleep "${2:-1.2}"; }
connect() { # (re)connect, waiting up to $1 seconds
  local deadline=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -le "$deadline" ]; do
    case "$SERIAL" in *:*) "$ADB" connect "$SERIAL" >/dev/null 2>&1 ;; esac
    [ "$(a get-state 2>/dev/null)" = device ] && return 0
    sleep 3
  done
  return 1
}
# Mean R G B and brightness spread of the area where FLauncher's side panel opens, from a raw screencap.
panel_area() {
  a exec-out screencap 2>/dev/null | python3 -I -c '
import sys, struct, statistics
d = sys.stdin.buffer.read(); w, h = struct.unpack("<II", d[:8]); off = len(d) - w * h * 4
px = [d[off + (y * w + x) * 4: off + (y * w + x) * 4 + 3] for y in range(700, 1000, 6) for x in range(1600, 1900, 6)]
mean = [sum(p[i] for p in px) // len(px) for i in range(3)]
print(*mean, int(statistics.pstdev([sum(p) for p in px])))'
}
# The panel is one flat colour; the wallpaper underneath isn't. Compare against a snapshot taken before.
panel_open() { local r g b sd; read -r r g b sd < <(panel_area); [ "$sd" -le 3 ] && [ "$*" != "$r $g $b $sd" ]; }
same_as() { local r g b sd; read -r r g b sd < <(panel_area); set -- $1; [ $(( (r - $1) * (r - $1) + (g - $2) * (g - $2) + (b - $3) * (b - $3) )) -lt 100 ]; }
focus_first_tv_tile() { key HOME 2; for _ in 1 2 3; do key DPAD_DOWN 0.6; done; for _ in 1 2 3 4 5; do key DPAD_LEFT 0.6; done; }

echo "TV: $SERIAL"
connect 20 || { echo "  FAIL  adb cannot reach $SERIAL"; exit 1; }

if [ "$reboot" = 1 ]; then
  echo "-- reboot"
  t0=$(date +%s)
  a reboot >/dev/null 2>&1
  sleep 20
  if ! connect 120; then
    note "adb is off after a reboot: turn on Settings > System > Developer options > Wireless debugging"
    if ! connect 600; then
      bad "TV did not come back on adb"; echo "$pass passed, $fail failed"; exit 1
    fi
  fi
  for _ in $(seq 1 60); do [ "$(sh_ getprop sys.boot_completed)" = 1 ] && break; sleep 3; done
  ok "adb back after reboot ($(( $(date +%s) - t0 )) s)"
  if r=$(wait_top "$FL_PKG" 90); then ok "boots to FLauncher (top after ${r} ms of waiting)"; else bad "after boot the top activity is $(top_activity)"; fi
fi

echo "-- system"
note "$(sh_ getprop ro.product.name) / Android $(sh_ getprop ro.build.version.release) / up $(sh_ uptime | sed -E 's/.*up +([^,]+),.*/\1/')"
home=$(sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
[ "$home" = "$FL_HOME" ] && ok "default Home is FLauncher" || bad "default Home is $home"
[ "$(sh_ getprop sys.boot_completed)" = 1 ] && ok "boot completed" || bad "boot not completed"
fl_ver=$(sh_ dumpsys package $FL_PKG | grep -m1 -oE 'versionName=[^ ]+')
[ -n "$fl_ver" ] && ok "FLauncher installed ($fl_ver)" || bad "FLauncher not installed"
[ -n "$(sh_ pidof $FL_PKG)" ] && ok "FLauncher running" || note "FLauncher not running (starts on Home)"

echo "-- packages"
disabled=$(sh_ pm list packages -d | sed 's/^package://')
installed=$(sh_ pm list packages | sed 's/^package://')
if [ -f "$APPLIED" ]; then
  while read -r pkg; do
    if grep -qx "$pkg" <<<"$disabled"; then ok "$pkg disabled"
    elif ! grep -qx "$pkg" <<<"$installed"; then ok "$pkg removed for this user"
    else bad "$pkg is enabled again"; fi
  done < <(grep -E '^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$' "$APPLIED")
else
  note "no $APPLIED; skipping the debloat list"
fi
for pkg in "${KEEP[@]}"; do
  if grep -qx "$pkg" <<<"$disabled"; then bad "$pkg is DISABLED (must stay enabled)"
  elif grep -qx "$pkg" <<<"$installed"; then ok "$pkg enabled"
  else bad "$pkg missing"; fi
done

echo "-- health"
loops=$(sh_ logcat -d -b crash | grep -oE 'Process: [^,]+' | sort | uniq -c | awk '$1 >= 3 {print $3 " x" $1}' | tr '\n' ' ')
[ -z "$loops" ] && ok "no crash loops in the crash log" || bad "crash loops: $loops"
anrs=$(sh_ logcat -d | grep -c 'ANR in')
[ "$anrs" = 0 ] && ok "no ANRs in the log" || bad "$anrs ANR(s) in the log"
pss=$(sh_ dumpsys meminfo $FL_PKG | grep -m1 'TOTAL PSS' | awk '{print $3}')
[ -n "$pss" ] && { [ "$pss" -lt 150000 ] && ok "FLauncher memory $((pss / 1024)) MB" || bad "FLauncher memory $((pss / 1024)) MB"; }
note "free memory $(( $(sh_ grep MemAvailable /proc/meminfo | awk '{print $2}') / 1024 )) MB of $(( $(sh_ grep MemTotal /proc/meminfo | awk '{print $2}') / 1024 )) MB"
sh_ ping -c1 -W3 8.8.8.8 >/dev/null && ok "internet reachable" || bad "no internet from the TV"
sh_ ping -c1 -W3 api.unsplash.com >/dev/null && ok "DNS works (api.unsplash.com)" || bad "DNS lookup failed"

if [ "$launch" = 1 ] || [ "$reboot" = 1 ]; then
  key HOME 3
fi
if [ "$(top_activity | cut -d/ -f1)" = $FL_PKG ]; then
  busy=$(sh_ top -b -n2 -d 4 | grep -E '^ *[0-9]+%cpu' | tail -1 | sed -E 's/.* ([0-9]+)%idle.*/\1/')
  total=$(sh_ top -b -n1 | grep -m1 -oE '^ *[0-9]+%cpu' | tr -dc 0-9)
  if [ -n "$busy" ] && [ -n "$total" ]; then
    used=$(( total - busy ))
    [ "$used" -lt $(( total / 6 )) ] && ok "idle CPU on Home: ${used}% of ${total}%" \
      || bad "CPU busy on an idle Home: ${used}% of ${total}% (is the highlight animation on?)"
  fi
else
  note "CPU check skipped: Home is not on screen ($(top_activity))"
fi

if [ "$launch" = 1 ]; then
  echo "-- launch"
  for pkg in com.google.android.youtube.tv in.startv.hotstar com.android.tv.settings com.android.vending; do
    a shell monkey -p "$pkg" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1
    if wait_top "$pkg" 15 >/dev/null; then
      sleep 2
      off=$(sh_ pm list packages -d | grep -cE '^package:(com.android.vending|com.google.android.gms)$')
      key HOME 0
      r=$(wait_top "$FL_PKG" 8) && ok "$pkg opens, Home returns in ${r} ms" || bad "$pkg: Home did not return to FLauncher"
      # The firmware turns Play Store and Play services off inside some apps and back on at a launcher it knows.
      if [ "$off" -gt 0 ]; then
        sleep 2
        [ "$(sh_ pm list packages -d | grep -cE '^package:(com.android.vending|com.google.android.gms)$')" = 0 ] \
          && ok "firmware turned Play Store off in $pkg, Home turned it back on" \
          || bad "Play Store / Play services still off after leaving $pkg (is FLauncher built as com.zeasn.launcher?)"
      fi
    else
      bad "$pkg did not open (top: $(top_activity))"; key HOME 2
    fi
  done
  focus_first_tv_tile
  before=$(panel_area)
  key MENU 2
  if panel_open "$before"; then ok "Menu key opens the app panel"; else bad "Menu key did not open the app panel"; fi
  key BACK 2
  if same_as "$before"; then ok "Back closes the app panel"; else bad "Back did not close the app panel"; fi
  for pkg in in.startv.hotstar com.android.vending; do
    focus_first_tv_tile
    wait_top "$FL_PKG" 10 >/dev/null || bad "FLauncher not on top before the tile test"
    [ "$pkg" = com.android.vending ] && key DPAD_RIGHT 1
    key DPAD_CENTER 0
    if wait_top "$pkg" 30 >/dev/null; then ok "tile opens $pkg"; sleep 3; else bad "tile did not open $pkg (top: $(top_activity))"; fi
    key HOME 3
  done
fi

if [ "$standby" = 1 ]; then
  echo "-- standby"
  key SLEEP 8
  state=$(sh_ dumpsys power | grep -m1 -oE 'mWakefulness=[A-Za-z]+' | cut -d= -f2)
  if [ -n "$state" ]; then
    [ "$state" != Awake ] && ok "standby: $state, adb still reachable" || bad "TV did not go to standby"
    key WAKEUP 6
    [ "$(sh_ dumpsys power | grep -m1 -oE 'mWakefulness=[A-Za-z]+' | cut -d= -f2)" = Awake ] && ok "woke up" || bad "did not wake"
  else
    note "adb lost in standby; wake the TV with the remote"
    if connect 180; then ok "adb back after wake"; else bad "adb did not come back within 3 minutes of standby"; fi
  fi
  key HOME 2
  r=$(wait_top "$FL_PKG" 15) && ok "FLauncher on screen after wake" || bad "after wake the top is $(top_activity)"
fi

echo "$pass passed, $fail failed"
[ "$fail" = 0 ]
