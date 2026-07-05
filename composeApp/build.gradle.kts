import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlinSerialization)
}

// Secrets read from local.properties, gradle properties, or environment variables.
// Key lookup is case-insensitive (checks key and key.lowercase()).
// Specifically for GEMINI_API_KEY, it also checks for "gemoni_api_key".
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String {
    val keys = mutableListOf(key, key.lowercase(), key.uppercase()).distinct()
    for (k in keys) {
        val v = localProps.getProperty(k)
            ?: project.findProperty(k)?.toString()
            ?: System.getenv(k)
        if (!v.isNullOrBlank()) return v
    }
    return ""
}

// On CI, the google-services.json is provided via an environment variable or local.properties.
// This block writes it to the expected location before the Google Services plugin runs.
secret("GOOGLE_SERVICES_JSON").takeIf { it.isNotBlank() }?.let { json ->
    val target = project.file("google-services.json")
    if (!target.exists()) {
        target.writeText(json)
    }
}

// Version is managed in playstore/version.properties (CI env overrides it).
val versionPropsFile = rootProject.file("playstore/version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}

kotlin {
    jvmToolchain(21)

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.identity.googleid)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.appcheck)
            implementation(libs.firebase.appcheck.playintegrity)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.google.code.scanner)
            implementation(libs.google.app.update.ktx)
            implementation(libs.coil.compose)
            // ARCore (Google Play Services for AR) + SceneView (Filament + Compose)
            // for the offline AR cellar screen. Android target only.
            implementation(libs.google.arcore)
            implementation(libs.sceneview.arsceneview)
            // BoofCV (pure JVM, no NDK): square-binary fiducial detection + pose for the
            // hybrid marker AR mode. boofcv-android adds GrayU8 <-> Bitmap conversion.
            implementation(libs.boofcv.core)
            implementation(libs.boofcv.android)
        }
    }
}

android {
    namespace = "fr.geoking.vincent"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    // deepboof:io (via BoofCV) pulls protobuf-java, which clashes with Firestore's protobuf-javalite.
    configurations.configureEach {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    defaultConfig {
        applicationId = "fr.geoking.vincent"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI overrides these via env so Play always gets an increasing versionCode.
        versionCode = (System.getenv("VERSION_CODE") ?: versionProps.getProperty("versionCode") ?: "1").toInt()
        versionName = (System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: versionProps.getProperty("versionName") ?: "1.0").removePrefix("v")
        buildConfigField("String", "WEB_CLIENT_ID", "\"${secret("WEB_CLIENT_ID")}\"")
        // AI calls go through the Cloudflare Worker proxy (key held server-side).
        // Set by ./scripts/setup-ai-proxy.sh (local.properties) or the CI secret.
        buildConfigField("String", "AI_PROXY_URL", "\"${secret("AI_PROXY_URL")}\"")
        // Feature flag for the ARCore "AR cellar" screen. Optional AR_ENABLED in
        // local.properties / CI env (true|false) flips it; defaults to enabled.
        buildConfigField("Boolean", "AR_ENABLED", secret("AR_ENABLED").ifBlank { "true" })
        // Wine data provider credentials/source. "xxx" placeholders mean "not
        // configured": the matching providers stay inert until a real value is set
        // via local.properties / gradle properties / CI env.
        buildConfigField("String", "GRAPEMINDS_API_KEY", "\"${secret("GRAPEMINDS_API_KEY").ifBlank { "xxx" }}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Release signing is driven by local.properties, gradle properties or env vars (set by the release workflow from
    // repository secrets). Absent locally → release stays unsigned, debug is fine.
    val keystorePath = secret("KEYSTORE_FILE")
    signingConfigs {
        create("release") {
            if (keystorePath.isNotBlank()) {
                storeFile = file(keystorePath)
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // The Gemini key is surfaced via BuildConfig.GEMINI_API_KEY.
        // It can be provided in local.properties, gradle properties, or as an env var.
        // Also supports "gemoni_api_key" for historical compatibility.
        val geminiKey = secret("GEMINI_API_KEY").ifBlank { secret("gemoni_api_key") }

        getByName("debug") {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
            if (keystorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        implementation(platform(libs.firebase.bom))
    }
}

// Room annotation processing for the Android target (entities/DAO live in androidMain).
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    // Debug-only App Check provider so local/dev builds can mint debug tokens
    // (register the printed debug token in the Firebase console). Release uses Play Integrity.
    add("debugImplementation", libs.firebase.appcheck.debug)
}
