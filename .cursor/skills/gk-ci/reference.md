# gk-ci reference

Detailed material for the `gk-ci` skill. Source of truth: `geoking-tools/INTEGRATION.md`.

## Gradle (`composeApp/build.gradle.kts`)

JDK 21 across CI, Gradle daemon, Kotlin toolchain. `gradle/gradle-daemon-jvm.properties` → `toolchainVersion=21`.

```kotlin
kotlin { jvmToolchain(21) }

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
```

### Secrets via local.properties / CI env

```kotlin
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(key: String) = localProps.getProperty(key) ?: System.getenv(key) ?: ""

android {
    defaultConfig {
        buildConfigField("String", "WEB_CLIENT_ID", "\"${secret("WEB_CLIENT_ID")}\"")
        // AI goes through the Cloudflare Worker proxy; the Gemini key stays server-side.
        buildConfigField("String", "AI_PROXY_URL", "\"${secret("AI_PROXY_URL")}\"")
    }
    buildTypes {
        // Never embed the Gemini key in release — only a debug-only dev fallback.
        getByName("debug")   { buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"") }
        getByName("release") { buildConfigField("String", "GEMINI_API_KEY", "\"\"") }
    }
    buildFeatures { buildConfig = true }
}
```

### Version (`playstore/version.properties`)

```kotlin
val versionProps = Properties().apply {
    rootProject.file("playstore/version.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

defaultConfig {
    versionCode = (System.getenv("VERSION_CODE") ?: versionProps.getProperty("versionCode") ?: "1").toInt()
    versionName = (System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: versionProps.getProperty("versionName") ?: "1.0").removePrefix("v")
}
```

CI injects `VERSION_CODE=${{ github.run_number }}`. Detect CI by a non-blank `VERSION_NAME` env (useful if auto-bumping the patch locally — skip the bump when CI sets `VERSION_NAME`).

### Signing (env vars, no committed keystore)

```kotlin
val keystorePath = System.getenv("KEYSTORE_FILE")
signingConfigs {
    create("release") {
        if (keystorePath != null) {
            storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}
buildTypes {
    getByName("release") {
        if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
    }
}
```

Absent locally → release stays unsigned; debug works with nothing configured.

## `.gitignore` additions

```gitignore
local.properties
composeApp/google-services.json

# Signing — never commit
*.keystore
*.jks
scripts/.keystore-credentials
scripts/.adb-wireless

# Generated Play release notes
playstore/whatsnew/
/secrets/*.json
```

## `playstore/`

`version.properties`:

```properties
versionCode=1
versionName=1.0.0
```

`whatsnew.xml` (source for `scripts/whatsnew.py 1` → `playstore/whatsnew/whatsnew-<locale>`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<releases>
  <release versionCode="1" versionName="1.0.0">
    <locale code="fr-FR">Première version.</locale>
    <locale code="en-US">First release.</locale>
  </release>
</releases>
```

## GitHub secrets

| Secret | Role |
|---|---|
| `KEYSTORE_BASE64` | upload keystore, base64 |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play API service account JSON |
| `GOOGLE_SERVICES_JSON` | `google-services.json`, base64 |
| `WEB_CLIENT_ID` | Web OAuth client (Firebase Auth) |
| `GEMINI_API_KEY` | Gemini key — also pushed as a **Worker Secret** (see AI proxy) |
| `CLOUDFLARE_API_TOKEN` | deploy the AI proxy Worker (and Pages) |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare account for the Worker/Pages |
| `AI_PROXY_URL` | Worker endpoint baked into release builds (`…/v1/generate`) |

## Manifest (`scripts/project.manifest.json`)

Copy `geoking-tools/templates/project.manifest.template.json`. Required fields:

| Section | Field | Example |
|---|---|---|
| `project` | `id` | `myapp-499318` (GCP/Firebase project id) |
| `project` | `name` | `MyApp` |
| `project` | `package` | `fr.geoking.myapp` |
| `build` | `gradleModule` | `:composeApp` |
| `build` | `googleServices` | `composeApp/google-services.json` |
| `build` | `keystoreAlias` | `key0` |
| `build` | `keystoreDn` | `CN=MyApp, OU=GeoKing, O=GeoKing, L=Paris, C=FR` |
| `build` | `mainActivity` | `.MainActivity` |
| `build` | `signInLogTag` | `MyAppSignIn` |
| `urls.play` | `developerId`, `appId` | Play Console IDs |
| `urls.*` | console links | Firebase, GCP, Play, Gemini, GitHub secrets |

Play URL pattern: `https://play.google.com/console/u/0/developers/{developerId}/app/{appId}/app-dashboard`

## OAuth client types in `google-services.json`

Google Sign-In needs **both** OAuth client types present in the Firebase project; the **code** only ever uses the **Web** one.

| `client_type` | Client | Role | Bound to | Used in code? |
|---|---|---|---|---|
| `1` | **Android** | Authorizes the app/device to request Google sign-in | `package_name` + **SHA-1** | No — Google matches the app signature automatically |
| `3` | **Web** ("server client ID") | Audience of the ID token Firebase verifies | nothing physical (server ID) | **Yes** — `setServerClientId(...)` / `default_web_client_id` / `WEB_CLIENT_ID` |

- `WEB_CLIENT_ID` (local.properties, BuildConfig, CI secret) is the **type 3 Web** client — never the Android one.
- Register **one type-1 client per fingerprint**: debug SHA-1, upload SHA-1, and Play App Signing SHA-1 (the last is required or sign-in breaks on Play even when debug works).
- Common failure: a `google-services.json` with only a type-3 client (or the wrong one) and no type-1 → "aucun jeton Google reçu" / `signInWithCredential` fails. Fix: `./scripts/pull-google-services.sh` re-downloads the file with all clients and resyncs `WEB_CLIENT_ID`.

## Local scripts (geoking-tools/bin via wrappers)

| Command | Use |
|---|---|
| `./scripts/setup-release.sh` | full release wizard (keystore, Play, Firebase, OAuth, Gemini) |
| `./scripts/show-secrets.sh` | local vs GitHub secrets recap |
| `./scripts/verify-oauth.sh` | Google Sign-In / SHA-1 check |
| `./scripts/pull-google-services.sh` | download `google-services.json` from Firebase (CLI) + sync `WEB_CLIENT_ID` in `local.properties`; `--push` also syncs the `GOOGLE_SERVICES_JSON` + `WEB_CLIENT_ID` GitHub secrets; alias `setup-release.sh config` |
| `./scripts/gen-keystore.sh` | generate release.keystore |
| `./scripts/build-aab.sh` | local signed AAB + fingerprint check |
| `./scripts/deploy-device.sh` | build APK + install on device |
| `./scripts/setup-ai-proxy.sh` | provision the Gemini proxy Worker (KV + `GEMINI_API_KEY` secret + deploy); `--push` syncs `CLOUDFLARE_*` / `AI_PROXY_URL` GitHub secrets |
| `./scripts/deploy-ai-proxy.sh` | redeploy the Worker (`wrangler deploy`) |
| `./scripts/adb-reconnect.sh` | wireless adb reconnect loop |
| `./scripts/whatsnew.py` | generate `playstore/whatsnew/` from `whatsnew.xml` |

The wrapper resolves geoking-tools via `GK_TOOLS`, then sibling `../geoking-tools`, then `~/dev/android/geoking-tools`.

## Reusable workflow inputs (geoking-ci)

`android-ci.yml`: `artifact_name` (required); defaults — `gradle_module=:composeApp`, `assemble_task=Debug`, `apk_glob=composeApp/build/outputs/apk/debug/*.apk`, `google_services_path`, `gradle_version=8.13`, `java_version`, `geoking_ci_repo`, `geoking_ci_ref=main`.

`release-play.yml`: `package_name` (required); defaults — `gradle_module=:composeApp`, `aab_glob=composeApp/build/outputs/bundle/release/composeApp-release.aab`, `google_services_path`, `whatsnew_script=scripts/whatsnew.py`, `default_track=internal`, `gradle_version=8.13`, `java_version=21`, plus `workflow_dispatch_track`, `workflow_dispatch_skip_review`, `is_workflow_dispatch`, `version_name_override`.

Pin `@main` for latest shared logic, or a tag/SHA for reproducibility.
