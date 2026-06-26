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
        buildConfigField("String", "GEMONI_API_KEY", "\"${secret("gemoni_api_key")}\"")
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
| `gemoni_api_key` | Gemini key (optional) |

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

## Local scripts (geoking-tools/bin via wrappers)

| Command | Use |
|---|---|
| `./scripts/setup-release.sh` | full release wizard (keystore, Play, Firebase, OAuth, Gemini) |
| `./scripts/show-secrets.sh` | local vs GitHub secrets recap |
| `./scripts/verify-oauth.sh` | Google Sign-In / SHA-1 check |
| `./scripts/gen-keystore.sh` | generate release.keystore |
| `./scripts/build-aab.sh` | local signed AAB + fingerprint check |
| `./scripts/deploy-device.sh` | build APK + install on device |
| `./scripts/adb-reconnect.sh` | wireless adb reconnect loop |
| `./scripts/whatsnew.py` | generate `playstore/whatsnew/` from `whatsnew.xml` |

The wrapper resolves geoking-tools via `GK_TOOLS`, then sibling `../geoking-tools`, then `~/dev/android/geoking-tools`.

## Reusable workflow inputs (geoking-ci)

`android-ci.yml`: `artifact_name` (required); defaults — `gradle_module=:composeApp`, `assemble_task=Debug`, `apk_glob=composeApp/build/outputs/apk/debug/*.apk`, `google_services_path`, `gradle_version=8.13`, `java_version`, `geoking_ci_repo`, `geoking_ci_ref=main`.

`release-play.yml`: `package_name` (required); defaults — `gradle_module=:composeApp`, `aab_glob=composeApp/build/outputs/bundle/release/composeApp-release.aab`, `google_services_path`, `whatsnew_script=scripts/whatsnew.py`, `default_track=internal`, `gradle_version=8.13`, `java_version=21`, plus `workflow_dispatch_track`, `workflow_dispatch_skip_review`, `is_workflow_dispatch`, `version_name_override`.

Pin `@main` for latest shared logic, or a tag/SHA for reproducibility.
