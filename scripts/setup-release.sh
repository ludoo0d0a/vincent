#!/usr/bin/env bash
#
# Vincent — guided release setup (init or replay).
#
# Walks you through everything needed to ship to Google Play from CI:
#   1. Signing keystore        → secrets KEYSTORE_BASE64/PASSWORD, KEY_ALIAS, KEY_PASSWORD
#   2. Play service account    → secret PLAY_SERVICE_ACCOUNT_JSON
#   3. Google Sign-In (OAuth)  → WEB_CLIENT_ID constant (+ Android client w/ SHA-1)
#   4. Gemini API key          → GEMINI_API_KEY constant
#
# Each step opens the right console page in your browser and uses the gh CLI to
# register secrets. Safe to re-run: it detects what already exists and asks.
#
# Usage:
#   ./scripts/setup-release.sh            # run every step
#   ./scripts/setup-release.sh keystore   # one step: keystore | play | oauth | gemini
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_ID="fr.geoking.vincent"
ALIAS="key0"
KS_PATH="$ROOT/release.keystore"
CRED="$ROOT/scripts/.keystore-credentials"
PKG_DIR="composeApp/src/androidMain/kotlin/fr/geoking/vincent"
SIGNIN_KT="$PKG_DIR/data/GoogleSignIn.android.kt"
WINEAI_KT="$PKG_DIR/ai/WineAi.android.kt"

# ---- helpers ---------------------------------------------------------------
c_bold=$'\033[1m'; c_dim=$'\033[2m'; c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
head_(){ printf '\n%s── %s ──%s\n' "$c_bold" "$*" "$c_off"; }
ok()   { printf '%s✓%s %s\n' "$c_ok" "$c_off" "$*"; }
warn() { printf '%s⚠%s  %s\n' "$c_warn" "$c_off" "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "❌ '$1' is required but not found."; exit 1; }; }
ask()  { local p="$1" a; read -r -p "$p " a; printf '%s' "$a"; }
confirm(){ local a; read -r -p "$1 [y/N] " a; [ "$a" = y ] || [ "$a" = Y ]; }
open_url(){ say "${c_dim}→ $1${c_off}"; open "$1" >/dev/null 2>&1 || xdg-open "$1" >/dev/null 2>&1 || true; }
sedi(){ if sed --version >/dev/null 2>&1; then sed -i "$@"; else sed -i '' "$@"; fi; }
patch_const(){ # file const value
  local f="$1" name="$2" val="$3"
  [ -f "$f" ] || { warn "$f not found — skipped $name."; return; }
  if grep -q "$name = \"" "$f"; then
    sedi -E "s#($name = \")[^\"]*(\")#\1$val\2#" "$f"
    ok "$name updated in $(basename "$f")"
    warn "This file is tracked by git — avoid committing a real key (consider a local.properties + BuildConfig)."
  else warn "$name not found in $f."; fi
}
set_local_prop(){ # key value  → writes to local.properties (gitignored)
  local key="$1" val="$2" f="$ROOT/local.properties"
  touch "$f"
  if grep -q "^$key=" "$f"; then sedi -E "s#^$key=.*#$key=$val#" "$f"; else printf '%s=%s\n' "$key" "$val" >> "$f"; fi
  ok "$key written to local.properties (gitignored)"
}
sha1(){ keytool -list -v -keystore "$KS_PATH" -alias "$ALIAS" -storepass "$1" 2>/dev/null \
        | awk -F'SHA1: ' '/SHA1:/{print $2; exit}'; }

# ---- step 1: keystore ------------------------------------------------------
step_keystore(){
  head_ "1. Signing keystore + GitHub secrets"
  need keytool; need gh; need openssl; need base64
  local PASS
  if [ -f "$KS_PATH" ] && [ -f "$CRED" ]; then
    ok "Existing keystore found."
    if confirm "Reuse it (re-push its secrets)?"; then
      PASS="$(grep '^KEYSTORE_PASSWORD=' "$CRED" | cut -d= -f2-)"
    elif confirm "Regenerate? This CHANGES the app signature (only before first publish!)"; then
      rm -f "$KS_PATH"; PASS=""
    else
      say "Keystore step skipped."; return
    fi
  fi
  if [ ! -f "$KS_PATH" ]; then
    PASS="$(openssl rand -base64 48 | LC_ALL=C tr -dc 'A-Za-z0-9')"; PASS="${PASS:0:28}"
    say "Generating $KS_PATH …"
    keytool -genkeypair -v -keystore "$KS_PATH" -alias "$ALIAS" \
      -keyalg RSA -keysize 2048 -validity 10000 -storepass "$PASS" -keypass "$PASS" \
      -dname "CN=Vincent, OU=GeoKing, O=GeoKing, L=Paris, C=FR" >/dev/null
    umask 077
    { echo "# DO NOT COMMIT — generated $(date -u +%FT%TZ)"
      echo "KEYSTORE_PASSWORD=$PASS"; echo "KEY_ALIAS=$ALIAS"; echo "KEY_PASSWORD=$PASS"; } > "$CRED"
    chmod 600 "$CRED"; ok "Keystore created; credentials saved (gitignored) → scripts/.keystore-credentials"
  fi
  local B64; B64="$(base64 < "$KS_PATH" | tr -d '\n')"
  printf '%s' "$B64"   | gh secret set KEYSTORE_BASE64
  printf '%s' "$PASS"  | gh secret set KEYSTORE_PASSWORD
  printf '%s' "$ALIAS" | gh secret set KEY_ALIAS
  printf '%s' "$PASS"  | gh secret set KEY_PASSWORD
  ok "Secrets set: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD"
  local S; S="$(sha1 "$PASS")"; [ -n "$S" ] && say "SHA-1 (keep for step 3): ${c_bold}$S${c_off}"
}

# ---- step 2: Play service account -----------------------------------------
step_play(){
  head_ "2. Google Play service account → PLAY_SERVICE_ACCOUNT_JSON"
  need gh
  say "a) Enable the Play Developer API (pick/create a Cloud project):"
  open_url "https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com"
  say "b) Create a service account (name e.g. vincent-ci):"
  open_url "https://console.cloud.google.com/iam-admin/serviceaccounts"
  say "   then in it: Keys → Add key → Create new key → JSON (downloads a file)."
  say "c) Grant it access in Play Console → Users and permissions → Invite,"
  say "   app permission 'Release manager':"
  open_url "https://play.google.com/console"
  local p; p="$(ask "Path to the downloaded service-account JSON (or blank to skip):")"
  if [ -n "$p" ]; then
    p="${p/#\~/$HOME}"
    [ -f "$p" ] || { warn "File not found: $p"; return; }
    gh secret set PLAY_SERVICE_ACCOUNT_JSON < "$p"
    ok "Secret set: PLAY_SERVICE_ACCOUNT_JSON"
  else say "Skipped (set later: gh secret set PLAY_SERVICE_ACCOUNT_JSON < file.json)."; fi
}

# ---- step 3: Google Sign-In OAuth -----------------------------------------
step_oauth(){
  head_ "3. Google Sign-In → WEB_CLIENT_ID"
  local S=""; [ -f "$CRED" ] && S="$(sha1 "$(grep '^KEYSTORE_PASSWORD=' "$CRED" | cut -d= -f2-)")"
  say "App package: ${c_bold}$APP_ID${c_off}"
  [ -n "$S" ] && say "SHA-1 for the Android client: ${c_bold}$S${c_off}"
  say "a) Configure the OAuth consent screen (once):"
  open_url "https://console.cloud.google.com/apis/credentials/consent"
  say "b) Create credentials → OAuth client ID:"
  open_url "https://console.cloud.google.com/apis/credentials"
  say "   • Android client: package '$APP_ID' + the SHA-1 above."
  say "   • Web application client: create it and COPY its client ID."
  local id; id="$(ask "Paste the WEB client ID (…apps.googleusercontent.com, blank to skip):")"
  [ -n "$id" ] && set_local_prop WEB_CLIENT_ID "$id" || say "Skipped."
}

# ---- step 4: Gemini key ----------------------------------------------------
step_gemini(){
  head_ "4. Gemini API key → GEMINI_API_KEY"
  say "Create a free API key in Google AI Studio:"
  open_url "https://aistudio.google.com/apikey"
  local k; k="$(ask "Paste the Gemini API key (blank to skip):")"
  [ -n "$k" ] && set_local_prop GEMINI_API_KEY "$k" || say "Skipped."
}

# ---- main ------------------------------------------------------------------
case "${1:-all}" in
  keystore) step_keystore ;;
  play)     step_play ;;
  oauth)    step_oauth ;;
  gemini)   step_gemini ;;
  all)      step_keystore; step_play; step_oauth; step_gemini ;;
  *) echo "usage: $0 [all|keystore|play|oauth|gemini]"; exit 2 ;;
esac

head_ "Done"
say "GitHub secrets:"
gh secret list 2>/dev/null | awk '{print "  • "$1}' || true
say "Then: push to main (or tag v*) → CI builds a signed AAB and uploads to the internal track."
say "(Play also needs the app to already exist with one manually-uploaded AAB.)"
