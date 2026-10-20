#!/usr/bin/env bash
# Disables (never uninstalls) the packages in a debloat list, or re-enables them.
#
#   scripts/debloat.sh debloat/<tv>.txt              disable every batch
#   scripts/debloat.sh debloat/<tv>.txt --batch 2    disable one batch ("# Batch 2: ..." in the list)
#   scripts/debloat.sh debloat/<tv>.txt --undo       re-enable everything in the list
#   scripts/debloat.sh debloat/<tv>.txt --dry-run    show what would change
#
# Before changing anything it saves the TV's enabled and disabled package lists to backups/.
set -euo pipefail
source "$(dirname "$0")/common.sh"

LIST=""
BATCH=""
UNDO=0
DRY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --batch) [[ $# -ge 2 ]] || die "--batch needs a number"; BATCH="$2"; shift ;;
    --undo) UNDO=1 ;;
    --dry-run) DRY=1 ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    -*) die "unknown option: $1" ;;
    *) LIST="$1" ;;
  esac
  shift
done
[[ -n "$LIST" && -f "$LIST" ]] || die "give a debloat list, e.g. debloat/sony-bravia-vh21.txt"

# Package names from the list, optionally from one batch only. Comments and blank lines are skipped.
packages() {
  awk -v want="$BATCH" '
    /^# *Batch [0-9]+/ { match($0, /[0-9]+/); batch = substr($0, RSTART, RLENGTH); next }
    /^[[:space:]]*(#|$)/ { next }
    want == "" || batch == want { print $1 }
  ' "$LIST"
}

require_device
PKGS=()
while IFS= read -r p; do PKGS+=("$p"); done < <(packages)
[[ ${#PKGS[@]} -gt 0 ]] || die "no packages found${BATCH:+ in batch $BATCH}"

if [[ $DRY == 0 ]]; then
  mkdir -p "$ROOT/backups"
  stamp="$(date +%Y%m%d-%H%M%S)"
  "$ADB" shell pm list packages -e | tr -d '\r' | sed 's/^package://' | sort > "$ROOT/backups/$stamp-enabled.txt"
  "$ADB" shell pm list packages -d | tr -d '\r' | sed 's/^package://' | sort > "$ROOT/backups/$stamp-disabled.txt"
  echo "Saved package lists to backups/$stamp-*.txt"
fi

# What this script itself disabled, per list, so --undo never enables something the TV shipped
# with disabled or that another tool turned off.
CHANGED="$ROOT/backups/changed-$(basename "$LIST" .txt).txt"
changed=0
for p in "${PKGS[@]}"; do
  if ! is_installed "$p"; then
    echo "  skip $p (not installed)"
    continue
  fi
  if [[ $UNDO == 1 ]]; then
    if [[ -f "$CHANGED" ]] && ! grep -qx "$p" "$CHANGED"; then
      echo "  skip $p (not disabled by this script)"
      continue
    fi
    [[ $DRY == 1 ]] && { echo "  would enable $p"; continue; }
    if "$ADB" shell pm enable --user 0 "$p" | tr -d '\r' | grep -q "new state: enabled"; then
      echo "  enabled $p"
      changed=$((changed + 1))
      [[ -f "$CHANGED" ]] && { grep -vx "$p" "$CHANGED" > "$CHANGED.tmp" || true; mv "$CHANGED.tmp" "$CHANGED"; }
    else
      echo "  warning: could not enable $p"
    fi
  else
    if [[ "$p" == "com.google.android.tungsten.setupwraith" ]] && wifi_owned_by_setup; then
      echo "  skip $p: it owns the saved Wi-Fi network. Forget and rejoin Wi-Fi in Settings first."
      continue
    fi
    [[ $DRY == 1 ]] && { echo "  would disable $p"; continue; }
    # One pm call per package: chained pm commands can stop after the first on some TVs.
    if "$ADB" shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r' | grep -q "new state: disabled"; then
      echo "  disabled $p"
      changed=$((changed + 1))
      grep -qx "$p" "$CHANGED" 2>/dev/null || echo "$p" >> "$CHANGED"
    else
      echo "  warning: could not disable $p"
    fi
  fi
done

echo
echo "Changed $changed package(s)."
if [[ $DRY == 0 && $UNDO == 0 && $changed -gt 0 ]]; then
  echo "Check Home, Settings, an HDMI input and a streaming app. Undo with: scripts/debloat.sh $LIST --undo"
fi
