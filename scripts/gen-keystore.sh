#!/usr/bin/env bash
#
# Generate an upload signing keystore for Vincent and the GitHub Actions secrets
# it needs (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).
#
# Usage:
#   ./scripts/gen-keystore.sh          # create keystore + save credentials locally
#   ./scripts/gen-keystore.sh --gh     # also push the 4 secrets to GitHub (needs gh, authenticated)
#
# The keystore (release.keystore) and scripts/.keystore-credentials are gitignored.
# KEEP THEM SAFE AND BACKED UP — losing the keystore means you can't update the app
# (unless you enrolled in Play App Signing).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KS_PATH="$ROOT/release.keystore"
CRED="$ROOT/scripts/.keystore-credentials"
ALIAS="vincent"
DNAME="CN=Vincent, OU=GeoKing, O=GeoKing, L=Paris, C=FR"

command -v keytool >/dev/null 2>&1 || { echo "❌ keytool not found — install a JDK (e.g. Temurin 17)."; exit 1; }

if [ -f "$KS_PATH" ]; then
  echo "⚠️  $KS_PATH already exists — refusing to overwrite your signing key."
  echo "    Delete it first if you really want a new one (this changes the app signature)."
  exit 1
fi

# Strong 28-char alphanumeric password (no shell-hostile chars, no SIGPIPE).
PASS="$(openssl rand -base64 48 | LC_ALL=C tr -dc 'A-Za-z0-9')"
PASS="${PASS:0:28}"

echo "🔐 Generating keystore → $KS_PATH"
keytool -genkeypair -v \
  -keystore "$KS_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "$DNAME" >/dev/null

B64="$(base64 < "$KS_PATH" | tr -d '\n')"

umask 077
cat > "$CRED" <<EOF
# Vincent signing credentials — DO NOT COMMIT. Generated $(date -u +%Y-%m-%dT%H:%M:%SZ)
KEYSTORE_PASSWORD=$PASS
KEY_ALIAS=$ALIAS
KEY_PASSWORD=$PASS
EOF
chmod 600 "$CRED"

echo "✅ Keystore created."
echo "🗝️  Credentials saved (gitignored): scripts/.keystore-credentials"
echo
echo "── Certificate fingerprints (for Google Sign-In / Firebase) ──"
keytool -list -v -keystore "$KS_PATH" -alias "$ALIAS" -storepass "$PASS" 2>/dev/null \
  | grep -E 'SHA1:|SHA256:' || true
echo

if [ "${1:-}" = "--gh" ]; then
  command -v gh >/dev/null 2>&1 || { echo "❌ gh CLI not found."; exit 1; }
  echo "── Pushing GitHub Actions secrets ──"
  printf '%s' "$B64"   | gh secret set KEYSTORE_BASE64
  printf '%s' "$PASS"  | gh secret set KEYSTORE_PASSWORD
  printf '%s' "$ALIAS" | gh secret set KEY_ALIAS
  printf '%s' "$PASS"  | gh secret set KEY_PASSWORD
  echo "✅ Set: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD"
  echo "   Still required (set manually): PLAY_SERVICE_ACCOUNT_JSON  (see README → Play service account)"
else
  echo "Next: push the secrets with"
  echo "    ./scripts/gen-keystore.sh --gh"
  echo "or read them from scripts/.keystore-credentials and KEYSTORE_BASE64 with:"
  echo "    base64 < release.keystore | tr -d '\\n' | pbcopy"
fi
