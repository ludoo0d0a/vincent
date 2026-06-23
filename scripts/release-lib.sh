#!/usr/bin/env bash
# Vincent — fonctions partagées (setup-release, verify-oauth, show-secrets).
# Usage :
#   ROOT="$(cd "$(dirname "$0")/.." && pwd)"
#   . "$ROOT/scripts/project.manifest.sh"
#   . "$ROOT/scripts/release-lib.sh"
#   vincent_lib_init "$ROOT"
set -euo pipefail

[[ -n "${VINCENT_LIB_LOADED:-}" ]] && return 0
VINCENT_LIB_LOADED=1

VINCENT_KEY_ALIAS="${VINCENT_KEY_ALIAS:-key0}"

# ---- UI (couleurs + mise en page) ------------------------------------------
c_reset=$'\033[0m'
c_bold=$'\033[1m'
c_dim=$'\033[2m'
c_ok=$'\033[32m'
c_warn=$'\033[33m'
c_err=$'\033[31m'
c_cyan=$'\033[36m'
c_link=$'\033[4;94m'   # bleu souligné (liens)
c_off=$'\033[0m'

if [ -n "${NO_COLOR:-}" ]; then
  c_bold= c_dim= c_ok= c_warn= c_err= c_cyan= c_link= c_off= c_reset=
fi

blank()  { printf '\n'; }
rule()   { printf '%s  ─────────────────────────────────────────────────────%s\n' "$c_dim" "$c_off"; }
say()    { printf '%s\n' "$*"; }

head_() {
  blank
  rule
  printf '  %s◆ %s%s\n' "$c_bold" "$*" "$c_off"
  rule
}

subhead() { blank; printf '  %s▸ %s%s\n' "$c_cyan" "$*" "$c_off"; }

ok()   { printf '  %s✓%s  %s\n' "$c_ok" "$c_off" "$*"; }
warn() { printf '  %s⚠%s   %s\n' "$c_warn" "$c_off" "$*"; }
fail() { printf '  %s✗%s  %s\n' "$c_err" "$c_off" "$*"; }
die()  { fail "$*"; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "Outil requis introuvable : $1"; }

hint() { printf '     %s│%s %s\n' "$c_dim" "$c_off" "$*"; }
code() { printf '     %s$%s %s\n' "$c_dim" "$c_off" "$*"; }
step() { printf '  %s●%s %s\n' "$c_cyan" "$c_off" "$*"; }

# Lien bleu souligné (terminal : Cmd+clic souvent supporté).
show_url() { printf '     🔗 %s%s%s\n' "$c_link" "$1" "$c_off"; }

# Libellé + lien alignés.
show_link() {
  local label="$1" url="$2"
  printf '     %-16s 🔗 %s%s%s\n' "$label" "$c_link" "$url" "$c_off"
}

# Bloc info (1–2 lignes de contexte).
info_box() {
  printf '  %s┌─%s\n' "$c_dim" "$c_off"
  while [ $# -gt 0 ]; do
    printf '  %s│%s %s\n' "$c_dim" "$c_off" "$1"
    shift
  done
  printf '  %s└─%s\n' "$c_dim" "$c_off"
}

vincent_lib_init() {
  ROOT="$1"
  GS="$ROOT/composeApp/google-services.json"
  LP="$ROOT/local.properties"
  KS_PATH="$ROOT/release.keystore"
  CRED="$ROOT/scripts/.keystore-credentials"
  ALIAS="$VINCENT_KEY_ALIAS"
}

# ---- SHA-1 -----------------------------------------------------------------
vincent_sha1_debug() {
  keytool -list -v -keystore "$HOME/.android/debug.keystore" -alias androiddebugkey \
    -storepass android -keypass android 2>/dev/null \
    | awk -F'SHA1: ' '/SHA1:/{print $2; exit}'
}

vincent_sha1_upload() {
  local pass="${1:-}"
  [ -n "$pass" ] || { [ -f "$CRED" ] && pass="$(grep '^KEYSTORE_PASSWORD=' "$CRED" | cut -d= -f2-)"; }
  [ -n "$pass" ] && [ -f "$KS_PATH" ] || return 0
  keytool -list -v -keystore "$KS_PATH" -alias "$ALIAS" -storepass "$pass" 2>/dev/null \
    | awk -F'SHA1: ' '/SHA1:/{print $2; exit}'
}

vincent_print_sha1_guide() {
  subhead "Empreintes SHA-1  ·  package $APP_ID"
  info_box \
    "Google autorise le sign-in seulement si le certificat qui a signé" \
    "l'APK installé est enregistré dans Firebase ou Google Cloud."
  blank
  hint "Où enregistrer les empreintes :"
  show_link "Firebase" "$FIREBASE_PROJECT"
  show_link "Google Cloud" "$GCP_CREDENTIALS"
  blank

  local d; d="$(vincent_sha1_debug)"
  printf '  %s① DEBUG%s  %sAndroid Studio · Run / installDebug%s\n' "$c_bold" "$c_off" "$c_dim" "$c_off"
  if [ -n "$d" ]; then hint "SHA-1 : ${c_bold}${d}${c_off}"
  else warn "~/.android/debug.keystore absent — lance l'app une fois dans Android Studio"
  fi
  hint "Si le sign-in échoue en local uniquement."
  blank

  printf '  %s② PLAY APP SIGNING%s  %s⚡ obligatoire Play Store%s\n' "$c_bold" "$c_off" "$c_warn" "$c_off"
  hint "Copier : Intégrité de l'app → clé de signature de l'application"
  show_link "Intégrité" "$PLAY_APP_INTEGRITY"
  show_link "Dashboard" "$PLAY_APP_DASHBOARD"
  show_link "Guide Google" "$PLAY_INTEGRITY_HELP"
  hint "Pas la clé d'upload — Google re-signe l'APK avant distribution."
  blank

  local u; u="$(vincent_sha1_upload)"
  printf '  %s③ UPLOAD KEY%s  %soptionnel · sideload release%s\n' "$c_bold" "$c_off" "$c_dim" "$c_off"
  if [ -n "$u" ]; then hint "SHA-1 : ${c_bold}${u}${c_off}"
  else hint "Généré par : ./scripts/setup-release.sh keystore"
  fi
  blank
  info_box "Résumé : debug Studio → ①  |  Play Store → ②  |  les deux → ① + ②"
}

vincent_print_console_checklist() {
  subhead "Consoles utiles"
  show_link "GCP" "$GCP_CONSOLE"
  show_link "OAuth" "$GCP_CREDENTIALS"
  show_link "Consentement" "$GCP_OAUTH_CONSENT"
  show_link "Firebase" "$FIREBASE_CONSOLE"
  show_link "Auth Google" "$FIREBASE_AUTH_GOOGLE"
  show_link "Users" "$FIREBASE_AUTH_USERS"
  show_link "Empreintes" "$FIREBASE_PROJECT"
  show_link "Play Console" "$PLAY_APP_DASHBOARD"
  blank
}

vincent_print_logcat_help() {
  subhead "Test sur appareil"
  code "adb logcat -c && adb logcat -s VincentSignIn"
  hint "Puis appuyer sur « Continuer avec Google » dans l'app."
  blank
  hint "Messages fréquents :"
  printf '     %s•%s WEB_CLIENT_ID is blank       → secret CI ou google-services.json manquant\n' "$c_dim" "$c_off"
  printf '     %s•%s signInWithCredential:failure → Google désactivé dans Firebase Auth\n' "$c_dim" "$c_off"
  printf '     %s•%s Caller not whitelisted / 16  → empreinte SHA-1 ① ou ② manquante\n' "$c_dim" "$c_off"
  blank
}

vincent_adb_status() {
  command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1 || return 0
  subhead "Appareil USB connecté"
  if adb shell pm path "$APP_ID" >/dev/null 2>&1; then
    ok "$APP_ID installé sur l'appareil"
    code "adb shell pm dump $APP_ID | grep -A2 signatures"
  else
    warn "$APP_ID non installé sur l'appareil"
  fi
  blank
}

# ---- Vérifications OAuth / Firebase ----------------------------------------
gh_secret_present() {
  command -v gh >/dev/null 2>&1 && gh secret list 2>/dev/null | awk '{print $1}' | grep -qx "$1"
}

vincent_check_google_services() {
  if [ ! -f "$GS" ]; then
    fail "composeApp/google-services.json manquant"
    hint "Place le fichier ici : composeApp/google-services.json"
    show_link "Télécharger" "$FIREBASE_PROJECT"
    return 1
  fi
  ok "composeApp/google-services.json présent"
  if ! command -v jq >/dev/null 2>&1; then
    warn "jq absent — package Android non vérifié"
    return 0
  fi
  local pkg
  pkg="$(jq -r '.client[0].client_info.android_client_info.package_name' "$GS")"
  if [ "$pkg" = "$APP_ID" ]; then
    ok "Package $APP_ID dans google-services.json"
  else
    fail "Package « $pkg » ≠ « $APP_ID » attendu"
    return 1
  fi
  return 0
}

vincent_require_google_services() {
  if ! vincent_check_google_services; then
    blank
    die "Fichier requis : composeApp/google-services.json"
  fi
}

vincent_push_google_services_secret() {
  need gh
  vincent_require_google_services
  base64 < "$GS" | tr -d '\n' | gh secret set GOOGLE_SERVICES_JSON
  ok "Secret GitHub GOOGLE_SERVICES_JSON enregistré"
}

vincent_check_web_client_id() {
  if [ ! -f "$LP" ] || ! grep -q '^WEB_CLIENT_ID=' "$LP"; then
    fail "WEB_CLIENT_ID manquant dans local.properties"
    return 1
  fi
  local web
  web="$(grep '^WEB_CLIENT_ID=' "$LP" | cut -d= -f2-)"
  if [ -z "$web" ]; then
    fail "WEB_CLIENT_ID vide dans local.properties"
    return 1
  fi
  if echo "$web" | grep -qE '^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$'; then
    ok "WEB_CLIENT_ID — format client Web OK (…${web##*-})"
  else
    fail "WEB_CLIENT_ID — format invalide (attendu …apps.googleusercontent.com)"
    return 1
  fi
  return 0
}

vincent_check_ci_web_client_id() {
  if grep -q 'WEB_CLIENT_ID: \${{ secrets.WEB_CLIENT_ID }}' "$ROOT/.github/workflows/release-play.yml" 2>/dev/null; then
    ok "Workflow release-play.yml injecte WEB_CLIENT_ID"
  else
    fail "release-play.yml n'injecte PAS WEB_CLIENT_ID"
    return 1
  fi
  if git show origin/main:.github/workflows/release-play.yml 2>/dev/null | grep -q 'WEB_CLIENT_ID'; then
    ok "origin/main contient WEB_CLIENT_ID"
  else
    warn "origin/main sans WEB_CLIENT_ID — build Play actuel peut être cassé"
  fi
  return 0
}

vincent_verify_oauth() {
  head_ "🔐  Vérification Google Sign-In / Firebase Auth"

  subhead "Firebase"
  vincent_check_google_services || true
  if gh_secret_present GOOGLE_SERVICES_JSON; then ok "Secret GitHub GOOGLE_SERVICES_JSON"
  else warn "Secret GOOGLE_SERVICES_JSON absent (CI)"; fi

  subhead "OAuth · WEB_CLIENT_ID"
  vincent_check_web_client_id || true
  if gh_secret_present WEB_CLIENT_ID; then ok "Secret GitHub WEB_CLIENT_ID"
  else warn "Secret WEB_CLIENT_ID absent ou gh non connecté"; fi

  subhead "CI GitHub Actions"
  vincent_check_ci_web_client_id || true

  vincent_print_sha1_guide
  vincent_print_console_checklist
  vincent_print_logcat_help
  vincent_adb_status
}

# ---- Helpers setup-release -------------------------------------------------
sedi(){ if sed --version >/dev/null 2>&1; then sed -i "$@"; else sed -i '' "$@"; fi; }

set_local_prop() {
  local key="$1" val="$2" f="$LP"
  touch "$f"
  if grep -q "^$key=" "$f"; then sedi -E "s#^$key=.*#$key=$val#" "$f"
  else printf '%s=%s\n' "$key" "$val" >> "$f"; fi
  ok "$key → local.properties"
}

ask()    { local p="$1" a; printf '  %s?%s %s' "$c_cyan" "$c_off" "$p"; read -r -p " " a; printf '%s' "$a"; }
confirm(){ local a; printf '  %s?%s %s' "$c_cyan" "$c_off" "$1 [o/N] "; read -r -p "" a
           [ "$a" = o ] || [ "$a" = O ] || [ "$a" = y ] || [ "$a" = Y ]; }
