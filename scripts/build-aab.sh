#!/usr/bin/env bash
#
# Vincent — build a SIGNED release AAB locally, with the correct upload key.
#
# Use this when Android Studio / IntelliJ signed with the wrong key: it signs
# with release.keystore (the cert Play expects, SHA1 78:38…) using the
# credentials in scripts/.keystore-credentials, then verifies the produced AAB's
# certificate fingerprint before you upload it to the Play Console.
#
# Usage:
#   ./scripts/build-aab.sh                 # version from playstore/version.properties
#   ./scripts/build-aab.sh 3               # versionCode 3
#   ./scripts/build-aab.sh 3 1.0.1         # versionCode 3, versionName 1.0.1
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

c_bold=$'\033[1m'; c_ok=$'\033[32m'; c_err=$'\033[31m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
die(){ printf '%s✗ %s%s\n' "$c_err" "$*" "$c_off" >&2; exit 1; }
ok(){  printf '%s✓ %s%s\n' "$c_ok" "$*" "$c_off"; }

# ---- signing credentials ---------------------------------------------------
CRED="$ROOT/scripts/.keystore-credentials"
KS="$ROOT/release.keystore"
[ -f "$KS" ]   || die "release.keystore introuvable à la racine du projet."
[ -f "$CRED" ] || die "scripts/.keystore-credentials introuvable."
KEYSTORE_PASSWORD="$(grep '^KEYSTORE_PASSWORD=' "$CRED" | cut -d= -f2-)"
KEY_ALIAS="$(grep '^KEY_ALIAS=' "$CRED" | cut -d= -f2-)"
KEY_PASSWORD="$(grep '^KEY_PASSWORD=' "$CRED" | cut -d= -f2-)"
[ -n "$KEYSTORE_PASSWORD" ] && [ -n "$KEY_ALIAS" ] || die "credentials incomplets dans $CRED."

# Expected upload-cert fingerprint, read straight from the keystore.
EXPECT_SHA1="$(keytool -list -v -keystore "$KS" -alias "$KEY_ALIAS" -storepass "$KEYSTORE_PASSWORD" 2>/dev/null \
               | awk -F'SHA1: ' '/SHA1:/{print $2; exit}')"
[ -n "$EXPECT_SHA1" ] || die "Impossible de lire l'empreinte du keystore (mauvais mot de passe/alias ?)."
printf 'Clé de signature : alias %s%s%s — SHA1 attendu %s%s%s\n' \
  "$c_bold" "$KEY_ALIAS" "$c_off" "$c_bold" "$EXPECT_SHA1" "$c_off"

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
  [ -n "$G" ] || die "Gradle introuvable. Ouvre le projet dans Android Studio une fois, ou installe gradle."
  GRADLE=("$G")
fi
ok "Gradle : ${GRADLE[*]}"

# ---- version overrides (optional) ------------------------------------------
[ -n "${1:-}" ] && export VERSION_CODE="$1"
[ -n "${2:-}" ] && export VERSION_NAME="$2"
[ -n "${VERSION_CODE:-}" ] && echo "versionCode forcé : $VERSION_CODE"
[ -n "${VERSION_NAME:-}" ] && echo "versionName forcé : $VERSION_NAME"

# ---- build -----------------------------------------------------------------
echo
echo "${c_dim}→ build du bundle release signé…${c_off}"
KEYSTORE_FILE="$KS" \
KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" \
KEY_ALIAS="$KEY_ALIAS" \
KEY_PASSWORD="$KEY_PASSWORD" \
"${GRADLE[@]}" :composeApp:bundleRelease --no-daemon --stacktrace

AAB="$ROOT/composeApp/build/outputs/bundle/release/composeApp-release.aab"
[ -f "$AAB" ] || die "AAB non produit ($AAB)."

# ---- verify the AAB was signed with the expected cert ----------------------
GOT_SHA1="$(keytool -printcert -jarfile "$AAB" 2>/dev/null \
            | awk -F'SHA1: ' '/SHA1:/{print $2; exit}')"
echo
ok "AAB : $AAB"
echo "SHA1 du bundle : ${c_bold}${GOT_SHA1:-?}${c_off}"
if [ "$GOT_SHA1" = "$EXPECT_SHA1" ]; then
  ok "Signature CONFORME à la clé attendue par Play. Prêt à uploader."
else
  die "Signature DIFFÉRENTE (attendu $EXPECT_SHA1). Ne pas uploader cet AAB."
fi
echo
echo "Uploade ce fichier dans Play Console → Test interne → Créer une release."
