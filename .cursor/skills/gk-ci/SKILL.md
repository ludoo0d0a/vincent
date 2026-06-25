---
name: gk-ci
description: >-
  Integrate the GeoKing shared CI/CD stack (geoking-ci reusable GitHub Actions +
  geoking-tools release/adb/build scripts) into a new or existing Android KMP
  (Compose) project. Use when the user wants to add Play Store release CI,
  signed AAB builds, OAuth/Firebase wiring, release secrets, version handling,
  or the gk/geoking scripts and workflows to an app, or mentions geoking-ci,
  geoking-tools, gk-ci, setup-release.sh, or release-play.yml.
---

# GeoKing CI/CD integration (gk-ci)

Wire an Android KMP/Compose app into the shared GeoKing stack:

- **[geoking-ci](https://github.com/ludoo0d0a/geoking-ci)** — public repo with **reusable** GitHub Actions workflows (`android-ci.yml`, `release-play.yml`) + the `setup-gradle` composite action. Must be public for `workflow_call` on GitHub Free.
- **[geoking-tools](https://github.com/ludoo0d0a/geoking-tools)** — shared shell/Python scripts (release wizard, adb, local build) consumed via thin wrappers in each app's `scripts/`.

The app keeps only thin callers/wrappers; all real logic lives in the two shared repos. **[vincent](https://github.com/ludoo0d0a/vincent)** is the reference app. The canonical guide is `geoking-tools/INTEGRATION.md` — read it for full detail; this skill is the operational summary.

## Prerequisites

| Tool | Use |
|---|---|
| `geoking-tools` cloned (sibling of the app, or `export GK_TOOLS=/path`) | scripts |
| `geoking-ci` pushed to GitHub (public) | reusable workflows |
| `jq`, `gh` (authed), JDK 21 | manifest, secrets, build |
| GCP/Firebase project + Play Console listing | IDs for the manifest |

Recommended layout (one tools copy for all apps):

```
~/dev/android/
├── geoking-tools/
├── geoking-ci/
├── vincent/
└── my-new-app/   ← target
```

## Fast path: bootstrap

From the app root, prefer the scaffolder over manual steps:

```bash
../geoking-tools/templates/bootstrap-new-app.sh --package fr.geoking.myapp --name MyApp
```

It creates `scripts/` (wrappers + `whatsnew.py` + `project.manifest.json`), `.github/workflows/{android-ci,release-play}.yml`, `.gitignore` entries, and a minimal `playstore/` (`version.properties`, `whatsnew.xml`). Idempotent: existing files are kept, not overwritten.

After bootstrap:

1. Edit `scripts/project.manifest.json` (console URLs, Play IDs).
2. Verify `package_name` in `release-play.yml` and `artifact_name` in `android-ci.yml`.
3. Apply the Gradle changes below (only on an existing app the scaffolder can't edit).
4. Run `./scripts/setup-release.sh`.
5. Push `geoking-ci` to GitHub (public) **before** the first CI run.

## Integration checklist (manual / existing project)

```
- [ ] 1. scripts/ + project.manifest.json
- [ ] 2. shell wrappers + whatsnew.py
- [ ] 3. .gitignore (secrets, keystore, generated whatsnew)
- [ ] 4. composeApp/build.gradle.kts (secrets, signing, version)
- [ ] 5. playstore/version.properties + whatsnew.xml
- [ ] 6. .github/workflows/{android-ci,release-play}.yml
- [ ] 7. ./scripts/setup-release.sh (keystore + secrets wizard)
- [ ] 8. push geoking-ci (public) before first CI run
```

Steps 4–6 are the parts the agent usually edits. See [reference.md](reference.md) for the exact Gradle snippets, the full secrets table, manifest fields, and reusable-workflow inputs.

## CI workflows

Each app ships ~15-line callers. Copy from `geoking-tools/templates/` (or let bootstrap do it):

`.github/workflows/android-ci.yml` — verify/build:

```yaml
jobs:
  build:
    uses: ludoo0d0a/geoking-ci/.github/workflows/android-ci.yml@main
    with:
      artifact_name: myapp-debug-apk   # ← rename
    secrets: inherit
```

`.github/workflows/release-play.yml` — Play release:

```yaml
jobs:
  release:
    uses: ludoo0d0a/geoking-ci/.github/workflows/release-play.yml@main
    with:
      package_name: fr.geoking.myapp   # ← replace
      is_workflow_dispatch: ${{ github.event_name == 'workflow_dispatch' && 'true' || 'false' }}
      workflow_dispatch_track: ${{ github.event_name == 'workflow_dispatch' && github.event.inputs.track || '' }}
      workflow_dispatch_skip_review: ${{ github.event_name == 'workflow_dispatch' && github.event.inputs.changesNotSentForReview && 'true' || 'false' }}
      version_name_override: ${{ github.ref_type == 'tag' && github.ref_name || '' }}
    secrets: inherit
```

Triggers belong to the **caller** (the app), not the reusable workflow (`on: workflow_call` only). Adjust `on:` here to control when CI/release run.

### CI behavior (default templates)

| Event | Workflow | Result |
|---|---|---|
| Push / PR `main` | `android-ci.yml` | debug APK artifact |
| Push `main` | `release-play.yml` | signed AAB → **internal** track |
| Tag `v*` | `release-play.yml` | `versionName` = tag name |
| `workflow_dispatch` | `release-play.yml` | choose Play track |

`versionCode` = `github.run_number` (injected by geoking-ci); `versionName` from `playstore/version.properties` unless overridden by a tag.

## Secrets (wizard, never by hand)

```bash
./scripts/setup-release.sh          # full guided setup
./scripts/setup-release.sh keystore # single step
./scripts/show-secrets.sh           # local vs GitHub recap
```

Required GitHub repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `GOOGLE_SERVICES_JSON`, `WEB_CLIENT_ID`, and `GEMINI_API_KEY` (if the app uses Gemini).

## First release order

Firebase (add app, download `google-services.json` → `composeApp/`) → keystore → Play Console (note `developerId`/`appId`) → service account → OAuth (register SHA-1 debug + Play App Signing) → verify → push `main` → first internal upload.

## Non-default Gradle module

If the module isn't `composeApp`, update `build.gradleModule` in the manifest **and** the workflow inputs (`gradle_module`, `apk_glob`, `aab_glob`).

## Additional resources

- [reference.md](reference.md) — Gradle snippets, secrets table, manifest fields, reusable-workflow inputs.
- Canonical, always-current: `geoking-tools/INTEGRATION.md`.
