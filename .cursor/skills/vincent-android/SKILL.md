---
name: vincent-android
description: >-
  Apply Google's android-skills to the Vincent KMP wine-cellar app. Use when
  doing Android platform work (edge-to-edge, Compose M3, testing, R8, AGP
  upgrades, Play integrations) in this repo. Bridges generic android-skills
  rules with Vincent's commonMain/androidMain split and current compliance state.
---

# Vincent × android-skills

Vincent is **Kotlin Multiplatform** with **all UI in `commonMain`** (Compose MP + Material 3).
Android-only code lives in `composeApp/src/androidMain/`. Read `README.md` and
`.cursor/rules/vincent-architecture.mdc` before exploring.

Generic android-skills live in `.cursor/rules/*.mdc` (installed via `android-skills-pack`).
This skill tells you **which apply**, **where in Vincent**, and **what is already done**.

## KMP constraints (always)

| Rule | Vincent |
|------|---------|
| UI changes | `composeApp/src/commonMain/kotlin/fr/geoking/vincent/` |
| Platform APIs (Room, Firebase, ARCore, camera, auth) | `androidMain/` via `expect`/`actual` |
| Navigation | Custom `Dest` stack in `App.kt` — **not** Navigation 3 / `NavHost` |
| Theme | `theme/Theme.kt` — light-only `VincentTheme`, lie-de-vin palette |
| State | Mutate via `Cellar` / `Racks` APIs — screens stay reactive |
| Secrets | `secret()` in `composeApp/build.gradle.kts` → `local.properties` (never commit) |

When an android-skill references `NavHost`, Hilt, or View-based XML, **adapt** to Vincent's
patterns instead of copying verbatim.

---

## Skill applicability matrix

| Skill (`.cursor/rules/`) | Status | Notes |
|--------------------------|--------|-------|
| `edge-to-edge` | **Active — gaps** | See § Edge-to-edge below |
| `jetpack-compose-m3` | **Active** | Compose MP M3; follow for new components |
| `styles` | **Active** | Extend `VincentColors` / `VincentTheme`, not raw Material defaults |
| `testing-setup` | **Partial** | Unit tests only; no Compose UI / instrumented tests yet |
| `android-cli` | **Active** | adb, logcat (`LogcatScreen`), Play in-app updates |
| `r8-analyzer` | **Future** | `isMinifyEnabled = false` in release — enable minify first |
| `agp-9-upgrade` | **Not yet** | AGP **8.13.2** (`gradle/libs.versions.toml`) |
| `navigation-3` | **Not applicable** | Custom stack; large migration — only if explicitly requested |
| `migrate-xml-views-to-jetpack-compose` | **N/A** | Already 100 % Compose |
| `play-billing-library-version-upgrade` | **N/A** | No Play Billing dependency |
| `camera1-to-camerax` | **N/A** | One-shot camera via `TakePicture` + `FileProvider` (`PhotoCapture.android.kt`) |
| `adaptive` | **Low** | Phone-first; no list-detail / window-size classes wired |
| `appfunctions`, `engage-sdk-integration`, `display-glasses-with-jetpack-compose-glimmer`, `verified-email`, `perfetto-*` | **N/A** | Not in scope |

---

## Edge-to-edge (`edge-to-edge.mdc`)

### Done

- `MainActivity.kt`: `enableEdgeToEdge()` with `SystemBarStyle.light(TRANSPARENT)` before `setContent`.
- `App.kt` root `Box`: `windowInsetsPadding(WindowInsets.systemBars)` — content stays inside bars.
- `MainScaffold`: `contentWindowInsets = WindowInsets(0)` and `NavigationBar(windowInsets = 0)` to avoid double padding.
- Form screens with text inputs use `imePadding()` on the root `Column`:
  `BottleEditScreen`, `AddScreen`, `TastingEditScreen`, `RackEditScreen`, `LogcatScreen`, `BottlesScreen`.
- `BottleFormPickers` fields inherit IME insets from parent form screens.

### Gaps

None known. When adding a new screen with `TextField` / `OutlinedTextField` / `BasicTextField`, apply
`imePadding()` on the root container (before `verticalScroll`) — match `BottleEditScreen.kt`.

---

## Jetpack Compose M3 (`jetpack-compose-m3.mdc`)

- Use `VincentTheme { }` wrapper (already in `App.kt`).
- Prefer `VincentColors.*` and `MaterialTheme.typography` over hard-coded colors.
- Shared widgets: `ui/Components.kt`, `ui/WineBottle.kt`, `ui/BottleFormPickers.kt`.
- Icons: `compose.material.icons` (extended set in `build.gradle.kts`).
- **Compose MP caveat**: use `org.jetbrains.compose.resources.stringResource` for strings, not `R.string`.

---

## Testing (`testing-setup.mdc`)

### Current

| Layer | Location | Framework |
|-------|----------|-----------|
| Common unit | `composeApp/src/commonTest/` | `kotlin("test")` — CSV, backup, PLOC import |
| Android unit | `composeApp/src/androidUnitTest/` | `VincentArchiveTest` |
| Worker e2e | `worker/test/*.mjs` | Node (AI proxy) |
| Compose UI / instrumented | — | **Not set up** |
| Screenshot | — | **Not set up** |

### When adding tests

- Pure logic → `commonTest` (preferred for `CsvFormat`, `Cellar` derivations).
- Android-specific (Room, archive) → `androidUnitTest`.
- Compose UI tests → add `androidx.compose.ui:ui-test-junit4` to `androidInstrumentedTest` source set; use `createComposeRule()` with Vincent composables (no Hilt).
- Follow `testing-setup.mdc` Steps 2–4; skip Hilt/Dagger sections.

---

## Build & release

| Item | Value / file |
|------|----------------|
| AGP | 8.13.2 |
| Kotlin | 2.3.21 · KSP 2.3.9 |
| SDK | min 24 · compile/target **36** |
| JDK | 21 (`jvmToolchain(21)`) |
| Minify | **off** (`release.isMinifyEnabled = false`) |
| Signing | `secret("KEYSTORE_*")` — CI via `.github/workflows/` |
| Version | `playstore/version.properties` |
| Play updates | `MainActivity` — flexible in-app updates (`app-update-ktx`) |

Before enabling R8/minify, run `r8-analyzer.mdc` and add keep rules for Room, Firebase, BoofCV, SceneView, ARCore.

---

## Android platform map (androidMain)

| Concern | File(s) |
|---------|---------|
| Bootstrap | `MainActivity.kt` |
| Room DB | `db/VincentDatabase.kt`, `db/*Entity.kt`, `db/*Dao.kt` |
| Auth | `data/Auth.android.kt`, `data/GoogleSignIn.android.kt` |
| Cloud sync | `data/CloudSync.android.kt` |
| Wine providers | `data/ProductLookup.android.kt`, `GrapeMindsClient.kt` |
| AI / Gemini | `ai/WineAi.android.kt`, `ai/GeminiClient.android.kt` |
| Camera / barcode / voice | `ai/PhotoCapture.android.kt`, `Barcode.android.kt`, `Dictation.android.kt` |
| AR cellar | `screens/ArScreen.android.kt`, `ar/ArProjection.kt` |
| File I/O | `data/FileTransfer.android.kt` |

---

## Workflow

1. Identify the android-skill (e.g. edge-to-edge, testing-setup).
2. Check the matrix above — skip N/A skills.
3. Read the Vincent file from the table, not the whole tree.
4. Apply changes in `commonMain` unless the skill is purely platform (then `androidMain`).
5. Match existing patterns (`BottleEditScreen` for IME, `App.kt` for insets, `Cellar` for state).
