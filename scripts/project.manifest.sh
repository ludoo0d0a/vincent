#!/usr/bin/env bash
# Vincent — project IDs & console URLs (single source of truth).
# Data lives in project.manifest.json; scripts source this file:
#   . "$(dirname "$0")/project.manifest.sh"
set -euo pipefail

_VINCENT_MANIFEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-${(%):-%x}}")" && pwd)"
_VINCENT_MANIFEST_JSON="$_VINCENT_MANIFEST_DIR/project.manifest.json"

_vincent_jq() {
  jq -r "$1" "$_VINCENT_MANIFEST_JSON"
}

_vincent_export() {
  printf 'export %s=%q\n' "$1" "$2"
}

_vincent_load_manifest() {
  [ -f "$_VINCENT_MANIFEST_JSON" ] || {
    echo "project.manifest.json introuvable: $_VINCENT_MANIFEST_JSON" >&2
    return 1
  }
  command -v jq >/dev/null 2>&1 || {
    echo "jq est requis pour lire project.manifest.json (brew install jq)" >&2
    return 1
  }

  local pkg
  pkg="$(_vincent_jq '.project.package')"

  _vincent_export PROJECT_ID "$(_vincent_jq '.project.id')"
  _vincent_export PROJECT_NAME "$(_vincent_jq '.project.name')"
  _vincent_export APP_PACKAGE "$pkg"
  _vincent_export APP_ID "$pkg"
  _vincent_export FIREBASE_CONSOLE "$(_vincent_jq '.urls.firebase.console')"
  _vincent_export FIREBASE_PROJECT "$(_vincent_jq '.urls.firebase.settings')"
  _vincent_export FIREBASE_AUTH_GOOGLE "$(_vincent_jq '.urls.firebase.authProviders')"
  _vincent_export FIREBASE_AUTH_USERS "$(_vincent_jq '.urls.firebase.authUsers')"
  _vincent_export GCP_CONSOLE "$(_vincent_jq '.urls.gcp.console')"
  _vincent_export GCP_CREDENTIALS "$(_vincent_jq '.urls.gcp.credentials')"
  _vincent_export GCP_OAUTH_CONSENT "$(_vincent_jq '.urls.gcp.oauthConsent')"
  _vincent_export GCP_PLAY_API "$(_vincent_jq '.urls.gcp.playDeveloperApi // .urls.gcp.console')"
  _vincent_export GCP_SERVICE_ACCOUNTS "$(_vincent_jq '.urls.gcp.serviceAccounts // .urls.gcp.console')"
  _vincent_export PLAY_DEVELOPER_ID "$(_vincent_jq '.urls.play.developerId')"
  _vincent_export PLAY_APP_ID "$(_vincent_jq '.urls.play.appId')"
  _vincent_export PLAY_APP_DASHBOARD "$(_vincent_jq '.urls.play.dashboard')"
  _vincent_export PLAY_APP_INTEGRITY "$(_vincent_jq '.urls.play.integrity')"
  _vincent_export PLAY_INTEGRITY_HELP "$(_vincent_jq '.urls.play.integrityHelp')"
  _vincent_export PLAY_CONSOLE "$(_vincent_jq '.urls.play.dashboard')"
  _vincent_export GEMINI_API_KEYS "$(_vincent_jq '.urls.gemini.apiKeys // "https://aistudio.google.com/apikey"')"
  _vincent_export GITHUB_ACTIONS_SECRETS "$(_vincent_jq '.urls.github.actionsSecrets // "https://github.com/settings/secrets/actions"')"
}

if [ -z "${VINCENT_MANIFEST_LOADED:-}" ]; then
  VINCENT_MANIFEST_LOADED=1
  eval "$(_vincent_load_manifest)"
fi
