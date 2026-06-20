#!/usr/bin/env bash
#
# Vincent — keep wireless adb alive by reconnecting in a loop.
#
# When the phone sleeps, Wi-Fi adb often drops. Run this in a terminal while
# developing; it reconnects silently and only prints when the status changes.
#
# Usage:
#   ./scripts/adb-reconnect.sh 192.168.1.42:5555       # reconnect every 30s
#   ./scripts/adb-reconnect.sh -s 192.168.1.42:5555    # save target, then run
#   ./scripts/adb-reconnect.sh                         # read scripts/.adb-wireless
#   ./scripts/adb-reconnect.sh -i 15 192.168.1.42:5555 # custom interval (seconds)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

INTERVAL=30
SAVE=0
TARGET=""

c_bold=$'\033[1m'; c_ok=$'\033[32m'; c_err=$'\033[31m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
die(){ printf '%s✗ %s%s\n' "$c_err" "$*" "$c_off" >&2; exit 1; }
ok(){  printf '%s✓ %s%s\n' "$c_ok" "$*" "$c_off"; }

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    -i|--interval) INTERVAL="${2:-}"; [ -n "$INTERVAL" ] || die "Missing value for $1"; shift 2 ;;
    -s|--save) SAVE=1; shift ;;
    -h|--help) usage ;;
    -*) die "Unknown option: $1 (try --help)" ;;
    *) TARGET="$1"; shift ;;
  esac
done

case "$INTERVAL" in
  ''|*[!0-9]*) die "Interval invalide : $INTERVAL (entier positif en secondes)" ;;
esac
[ "$INTERVAL" -gt 0 ] || die "Interval invalide : $INTERVAL"

ADB="$(command -v adb 2>/dev/null || true)"
[ -n "$ADB" ] || die "adb introuvable. Installe Android SDK platform-tools ou ouvre Android Studio."

# shellcheck source=adb-wireless.sh
source "$ROOT/scripts/adb-wireless.sh"

TARGET="$(adb_wireless_resolve_target "$TARGET")"
[ -n "$TARGET" ] || die "Cible manquante. Passe IP:PORT ou enregistre-la avec -s (ex. ./scripts/adb-reconnect.sh -s 192.168.1.42:5555)."

if [ "$SAVE" -eq 1 ]; then
  adb_wireless_save_target "$TARGET"
  ok "Cible enregistrée dans scripts/.adb-wireless"
fi

trap 'printf "\n%sArrêt.%s\n" "$c_dim" "$c_off"; exit 0' INT TERM

ok "Reconnexion adb vers ${c_bold}${TARGET}${c_off} toutes les ${INTERVAL}s (Ctrl+C pour arrêter)"
printf '%sAstuce : garde le Wi-Fi actif en veille + « Rester activé » (options développeur).%s\n\n' "$c_dim" "$c_off"

adb_wireless_reconnect_loop "$TARGET" "$INTERVAL"
