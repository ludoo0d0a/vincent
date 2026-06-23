#!/usr/bin/env bash
# Vincent — vérifie la config Google Sign-In / Firebase Auth.
# Alias : ./scripts/setup-release.sh verify
#
# Usage : ./scripts/verify-oauth.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=project.manifest.sh
. "$ROOT/scripts/project.manifest.sh"
# shellcheck source=release-lib.sh
. "$ROOT/scripts/release-lib.sh"
vincent_lib_init "$ROOT"

vincent_verify_oauth
