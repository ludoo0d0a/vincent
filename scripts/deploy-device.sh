#!/usr/bin/env bash
#
# Vincent — build a debug APK and install it on a connected device or emulator.
#
# Usage:
#   ./scripts/deploy-device.sh              # install on the only connected device
#   ./scripts/deploy-device.sh -l           # install and launch the app
#   ./scripts/deploy-device.sh -s SERIAL    # pick a device (adb devices)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_ID="fr.geoking.vincent"
LAUNCH_ACTIVITY="$APP_ID/.MainActivity"

c_bold=$'\033[1m'; c_ok=$'\033[32m'; c_err=$'\033[31m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
die(){ printf '%s✗ %s%s\n' "$c_err" "$*" "$c_off" >&2; exit 1; }
ok(){  printf '%s✓ %s%s\n' "$c_ok" "$*" "$c_off"; }

LAUNCH=0
DEVICE=""

usage() {
  sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    -l|--launch) LAUNCH=1; shift ;;
    -s|--device) DEVICE="${2:-}"; [ -n "$DEVICE" ] || die "Missing value for $1"; shift 2 ;;
    -h|--help) usage ;;
    *) die "Unknown option: $1 (try --help)" ;;
  esac
done

# ---- adb -------------------------------------------------------------------
ADB="$(command -v adb 2>/dev/null || true)"
[ -n "$ADB" ] || die "adb introuvable. Installe Android SDK platform-tools ou ouvre Android Studio."

mapfile -t DEVICES < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [ "${#DEVICES[@]}" -eq 0 ]; then
  die "Aucun appareil connecté. Branche un téléphone (USB debugging) ou lance un émulateur."
fi

if [ -n "$DEVICE" ]; then
  found=0
  for d in "${DEVICES[@]}"; do
    [ "$d" = "$DEVICE" ] && found=1 && break
  done
  [ "$found" -eq 1 ] || die "Appareil '$DEVICE' introuvable. adb devices : ${DEVICES[*]}"
else
  if [ "${#DEVICES[@]}" -gt 1 ]; then
    die "Plusieurs appareils (${DEVICES[*]}). Passe -s SERIAL."
  fi
  DEVICE="${DEVICES[0]}"
fi
export ANDROID_SERIAL="$DEVICE"
ok "Appareil : $DEVICE"

# ---- JDK 17 ----------------------------------------------------------------
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME:-}/bin/javac" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if [ -z "${JAVA_HOME:-}" ]; then
  for j in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
           "$HOME/Library/Java/JavaVirtualMachines"/*/Contents/Home; do
    [ -x "$j/bin/javac" ] && { JAVA_HOME="$j"; break; }
  done
fi
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] || die "JDK introuvable. Définis JAVA_HOME (JDK 17)."
export JAVA_HOME
ok "JDK : $JAVA_HOME"

# ---- Gradle (prefer wrapper, then cached 8.13, then PATH) ------------------
if [ -x "$ROOT/gradlew" ]; then
  GRADLE=("$ROOT/gradlew")
else
  G="$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle 2>/dev/null | head -1)"
  [ -z "$G" ] && G="$(ls -d "$HOME"/.gradle/wrapper/dists/gradle-*/*/gradle-*/bin/gradle 2>/dev/null | sort -V | tail -1)"
  [ -z "$G" ] && G="$(command -v gradle 2>/dev/null || true)"
  [ -n "$G" ] || die "Gradle introuvable. Lance 'gradle wrapper --gradle-version 8.13' ou ouvre le projet dans Android Studio."
  GRADLE=("$G")
fi
ok "Gradle : ${GRADLE[*]}"

# ---- build + install -------------------------------------------------------
echo
echo "${c_dim}→ build et installation debug sur $DEVICE…${c_off}"
"${GRADLE[@]}" :composeApp:installDebug --no-daemon --stacktrace

APK="$ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"
[ -f "$APK" ] && ok "APK : $APK"

if [ "$LAUNCH" -eq 1 ]; then
  echo
  echo "${c_dim}→ lancement de Vincent…${c_off}"
  "$ADB" -s "$DEVICE" shell am start -n "$LAUNCH_ACTIVITY" >/dev/null
  ok "App lancée ($LAUNCH_ACTIVITY)"
fi

echo
ok "Déployé sur ${c_bold}$DEVICE${c_off}."
