# Vincent — wine cellar (Kotlin Multiplatform)

A "block/rack-type" wine cellar manager — a pun on *vin* (wine). Visual direction:
*modern minimal*, lie-de-vin accent, status by wine colour (red / white / rosé /
sparkling). Features inspired by PLOC and Vivino, UI inspired by myBar.

All UI lives in **`commonMain`** (Compose Multiplatform) — ready to share with
iOS/desktop later; only the **Android** target is wired for now.

## Stack

| | |
|---|---|
| Language | Kotlin 2.3.21 (KSP 2.3.9) |
| UI | Compose Multiplatform 1.11.1 (Material 3) |
| Build | AGP 8.13.2 · Gradle 8.13 |
| Toolchain | JDK 17 (build via `jvmToolchain(17)` + daemon via `gradle/gradle-daemon-jvm.properties`, auto-provisioned by foojay) |
| SDK | min 24 · compile/target 36 |

## Run

The binary Gradle wrapper (`gradle-wrapper.jar` + `gradlew`) is not committed.
Two options:

**Android Studio (recommended)** — *Ladybug* or newer: open the `vincent/`
folder, let it sync, then ▶ on the `composeApp` run configuration.

**Command line** — generate the wrapper once, then build:

```bash
gradle wrapper --gradle-version 8.13
./gradlew :composeApp:installDebug    # on a connected device/emulator
```

## Screens

Material bottom-nav with 4 tabs (Home · Racks · Bottles · Search), an add FAB,
plus surfaces reached by navigation:

| # | Screen | File |
|---|---|---|
| 1 | Dashboard (value, colour donut) | `screens/DashboardScreen.kt` |
| 2 | Racks — display mode **Colour · Price · Vintage · Category** | `screens/CellarScreen.kt` |
| 3 | Bottle list / grid | `screens/BottlesScreen.kt` |
| 4 | Detail — food pairings, drink window, origin/merchant/occasion | `screens/BottleDetailScreen.kt` |
| 5 | Add — scan / photo / voice / manual form | `screens/AddScreen.kt` |
| 6 | Search & filters (colour, price, origin, merchant, occasion) | `screens/SearchScreen.kt` |
| 7 | Google sign-in (cloud + favourites) | `screens/LoginScreen.kt` |
| 8 | Account & favourites | `screens/AccountScreen.kt` |
| 9 | Recently added (by date + source) | `screens/RecentScreen.kt` |

## Architecture

```
composeApp/src/
├── androidMain/fr.geoking.vincent/
│   ├── MainActivity.kt     # Android entry point: build Room + Cellar.bootstrap
│   └── db/                 # BottleEntity, BottleDao, VincentDatabase, RoomCellarRepository
└── commonMain/fr.geoking.vincent/
    ├── App.kt              # root navigation (Scaffold + bottom bar + overlays)
    ├── theme/Theme.kt      # lie-de-vin palette + Material3 + type
    ├── model/              # Models.kt (WineColor, Bottle, RackCell…) + SampleData.kt
    ├── data/               # Cellar (reactive store) + CellarRepository (persistence seam)
    ├── ui/                 # WineBottle (Canvas), ColorTag, Stars, ScreenHeader, VCard
    └── screens/            # the 9 screens
```

## Notes

- **State** — `data/Cellar.kt` is the single source of truth (Compose snapshot
  state), seeded by `SampleData`. Screens are **reactive**: adding a bottle
  (Add → Confirm), serving −1 / +1, (un)favouriting and filtering actually mutate
  the data, and the dashboard / favourites / recents recompute. The API is kept
  narrow (`addBottle` / `adjustQuantity` / `toggleFavorite` + derived reads) so a
  persistent implementation can replace the in-memory lists without touching the UI.
- **Persistence: Room (wired).** `CellarRepository` (commonMain) is implemented by
  `RoomCellarRepository` (androidMain) over a Room database (`db/VincentDatabase`,
  `BottleEntity`, suspend DAO). `MainActivity` builds the DB and calls
  `Cellar.bootstrap(repo)`: on first launch the seed is persisted, afterwards the
  stored bottles replace the seed, and every add / serve / favourite is written
  through. Room runs on the Android target via KSP (`kspAndroid`); enums are stored
  by name, so no `TypeConverter`.
- **Google sign-in (Firebase Auth).** **Credential Manager** → Google ID token →
  **Firebase Authentication** (users in Firebase Console). Android `actual`
  (`data/GoogleSignIn.android.kt`); session via `data/Auth.android.kt` listener;
  `bootstrapAuth()` in `MainActivity`. Account screen shows name/email + sign out.
  ⚠️ **Required**: `composeApp/google-services.json` from Firebase (project
  `vincent-499318`, package `fr.geoking.vincent`) + **Google** provider enabled in
  Firebase Authentication. `./scripts/setup-release.sh firebase` sets it up (Firebase
  CLI if `firebase login`, else manual download); CI reads secret `GOOGLE_SERVICES_JSON`.
- **CSV import / export (wired).** `data/CsvFormat.kt` serialises the cellar
  (Vincent format, round-trip) and parses an incoming CSV with **tolerant column
  mapping**: detects Vincent / Vivino / PLOC / spreadsheet via headers (FR/EN) and
  maps colour, vintage, price, region, rating… File access goes through the Storage
  Access Framework (`data/FileTransfer*.kt`, `expect/actual`). An **Import / Export**
  screen is reachable from Account.
- **Recognition & price (AI, wired).** `ai/WineAi.kt` exposes `WineRecognizer`
  (title/photo → `Bottle`) and `PriceEstimator` (→ estimated price). The Android
  `actual` `ai/WineAi.android.kt` calls **Gemini Flash** (HTTP + `org.json`,
  structured JSON output). Wired into the Add screen (scan/photo): an
  "identify with AI" button fills the fields + an estimated price, then the add
  reuses those values. ⚠️ Set `GEMINI_API_KEY` in `local.properties` (gitignored,
  via `BuildConfig`; free key at aistudio.google.com) — otherwise it no-ops cleanly.
  Price is always shown as an **estimate** (source displayed).
- **Food pairings (AI, wired).** `FoodPairer` (same `GeminiClient`): the bottle
  detail has a "Suggest more pairings (AI)" button → Gemini returns dishes, merged
  with the existing pairings.
- **Voice dictation (wired).** `ai/Dictation.kt` (expect) + `Dictation.android.kt`
  (`android.speech.SpeechRecognizer`, fr-FR, free/offline): live transcript + mic
  level for the waveform, `RECORD_AUDIO` requested on tap. The final transcript is
  passed to `WineRecognizer.fromText` (Gemini) which fills the fields; the Voice
  screen shows the real waveform, transcript and result.
- **Label photo (wired).** `ai/PhotoCapture.kt` (expect) + `PhotoCapture.android.kt`:
  **system** camera (`TakePicture` + `FileProvider`, full resolution, **no CameraX**
  — overkill for a one-shot snap), CAMERA permission on tap. In Photo mode the button
  captures → `WineRecognizer.fromImage` (Gemini) fills the fields + price.
- **Barcode (wired).** `ai/Barcode.kt` (expect) + `Barcode.android.kt`: **Google Code
  Scanner** (`play-services-code-scanner`, no camera permission, module preloaded via
  the manifest `mlkit.vision.DEPENDENCIES` meta-data) reads the EAN/UPC. `data/ProductLookup.kt`
  (expect) + `ProductLookup.android.kt` resolves it against **Open Food Facts** (free,
  no key) and prefills the **manual** form (name/brand). EANs rarely encode vintage/price,
  so the user completes those — or falls back to the label photo (AI).
- **Manual entry (wired).** A real form in the Add screen (`screens/AddScreen.kt`) for
  domaine/appellation/colour/category/vintage/price/quantity/rack — the reliable path when
  AI/lookup miss. The confirm button stays disabled until there is a real bottle.
- **In-app updates (wired).** `MainActivity` uses **Play In-App Updates** (flexible):
  on startup it checks Play, shows the update popup, downloads in the background, and
  **auto-completes (restarts) as soon as the download finishes**. Only active for
  Play-installed builds; a no-op in debug/sideload.
- **Still to wire**: effective cloud sync of the Room data to the account.
- **Bottles** are drawn vectorially (`ui/WineBottle`) — capsule, body, label — so
  they stay crisp at any size with no bitmap assets. Replaceable with real photos later.
- **Launcher icon (wired).** The brand PNG `playstore/icon-512.png` (wine glass on a
  lie-de-vin gradient): used full-bleed as the adaptive **background** bitmap
  (`drawable-nodpi/ic_launcher_bg.png`, transparent `drawable/ic_launcher_fg.xml`
  foreground → no white-border legacy treatment on API 26+), with square PNG mipmaps
  (`mipmap-*dpi/`) as the API 24–25 fallback.
- **No fullscreen / insets.** The app stays within the system bars: the root applies
  `WindowInsets.systemBars` padding (and the Scaffold/NavigationBar add none), so the
  top back button and bottom navigation are never hidden under the status/nav bars.

## Consoles & project manifest

IDs and console URLs live in **`scripts/project.manifest.json`** (per-project config;
shared logic in sibling **[geoking-tools](https://github.com/ludoo0d0a/geoking-tools)**).
Edit the JSON once; scripts and docs stay in sync.

| Console | Lien |
|---------|------|
| **Firebase** (`vincent-499318`) | [console.firebase.google.com/project/vincent-499318/](https://console.firebase.google.com/project/vincent-499318/) |
| **Google Cloud** (`vincent-499318`) | [console.cloud.google.com/welcome?project=vincent-499318](https://console.cloud.google.com/welcome?project=vincent-499318) |
| **Play Console** — Vincent (dashboard) | [app-dashboard](https://play.google.com/console/u/0/developers/8648842673731499425/app/4975982411132001122/app-dashboard) |
| Play — Intégrité / SHA-1 app signing | [keymanagement](https://play.google.com/console/u/0/developers/8648842673731499425/app/4975982411132001122/keymanagement) |
| Firebase Auth → Google | [authentication/providers](https://console.firebase.google.com/project/vincent-499318/authentication/providers) |
| Firebase Auth → Users | [authentication/users](https://console.firebase.google.com/project/vincent-499318/authentication/users) |
| GCP OAuth credentials | [apis/credentials](https://console.cloud.google.com/apis/credentials?project=vincent-499318) |

Package Android : `fr.geoking.vincent` · Play developer `8648842673731499425` ·
Play app `4975982411132001122`.

## CI/CD (GitHub Actions)

Workflows in `.github/workflows/` are thin callers to reusable workflows in
**[geoking-ci](https://github.com/ludoo0d0a/geoking-ci)** (JDK 17 + Gradle 8.13,
pinned because the binary wrapper is not committed → call `gradle …`):

- **`android-ci.yml`** — on push / PR to `main`: `assembleDebug` + APK artifact.
- **`release-play.yml`** — **on every push to `main`** (track **internal**), plus on
  `v*` tags and manual dispatch (track choice): builds a **signed AAB** and uploads
  to Google Play (`r0adkll/upload-google-play`). Deploys are serialized
  (`concurrency: play-release`).

`versionCode` is derived from `github.run_number`, `versionName` is the tag on tag
pushes (else `1.0.<run>`) — no manual bump. The release job signs from env vars; a
local release build stays unsigned (debug works with nothing configured).

> **Fastest path:** run `./scripts/setup-release.sh` — a guided, re-runnable
> wizard that generates the keystore, opens each Google console page, and registers
> every secret via `gh`. Run a single step with
> `./scripts/setup-release.sh keystore|play|firebase|oauth|gemini|secrets|verify`.
> Scripts partagés : clone `geoking-tools` next to this repo (or set `GEOKING_TOOLS`).

### Repository secrets to create (Settings → Secrets and variables → Actions)

| Secret | What it is |
|---|---|
| `KEYSTORE_BASE64` | the upload keystore, base64-encoded (see below) |
| `KEYSTORE_PASSWORD` | the keystore (store) password |
| `KEY_ALIAS` | the key alias inside the keystore |
| `KEY_PASSWORD` | the key password (often same as the store password) |
| `PLAY_SERVICE_ACCOUNT_JSON` | full JSON of a Google Play service account (see below) |

#### Where to get them — signing keystore

1. **Create the keystore** (once; keep `release.keystore` safe and backed up — if you
   lose it you can't update the app, unless you use Play App Signing):

   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias vincent \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   `keytool` ships with the JDK. It will prompt for:
   - a **keystore password** → this is `KEYSTORE_PASSWORD`
   - your name/org (cosmetic, in the certificate)
   - a **key password** (press Enter to reuse the keystore one) → `KEY_PASSWORD`

   The `-alias` you chose (`vincent` here) is `KEY_ALIAS`.

2. **Encode it to base64** for the secret (it must be a single line, no wrapping):

   ```bash
   # macOS
   base64 -i release.keystore | pbcopy
   # Linux
   base64 -w0 release.keystore
   ```

   Paste the result into `KEYSTORE_BASE64`.

3. *(Optional)* the **SHA-1 fingerprint** needed for Google Sign-In comes from the
   same keystore:

   ```bash
   keytool -list -v -keystore release.keystore -alias vincent
   ```

#### Where to get them — Play service account

1. **Google Cloud Console** → the project linked to your Play account → *IAM & Admin
   → Service Accounts* → **Create service account**.
2. Create a **JSON key** for it (Keys → Add key → JSON) and download the file. Its
   full contents go into `PLAY_SERVICE_ACCOUNT_JSON`.
3. **Google Play Console** → *Users and permissions* → **Invite** that service-account
   email and grant it **Release manager** (or *Admin*) on the app. Ensure the *Google
   Play Android Developer API* is enabled in Cloud Console.

### Play prerequisites

1. The app must **already exist** on the Play Console with **a first AAB uploaded
   manually** (the API does not create the app).
2. The `applicationId` `fr.geoking.vincent` must match the one in the Console.
3. The service account must be linked under Play Console → Users and permissions.

## Landing page

A static marketing site lives in `website/` and is published by Netlify
(`netlify.toml`, `publish = "website"`, no build step). It reuses the app's
lie-de-vin palette and renders live phone-frame "screenshots". Connect the repo in
Netlify (or drag-and-drop the `website/` folder) — pushes to `main` redeploy it.
