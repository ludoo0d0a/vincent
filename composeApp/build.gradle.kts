import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// On CI, the google-services.json is provided via an environment variable.
// This block writes it to the expected location before the Google Services plugin runs.
System.getenv("GOOGLE_SERVICES_JSON")?.takeIf { it.isNotBlank() }?.let { json ->
    val target = project.file("google-services.json")
    if (!target.exists()) {
        target.writeText(json)
    }
}

// Secrets read from local.properties (or CI env), surfaced via BuildConfig — never hardcoded.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = localProps.getProperty(key) ?: System.getenv(key) ?: ""

// Version is managed in playstore/version.properties (CI env overrides it).
val versionPropsFile = rootProject.file("playstore/version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}

// Auto-bump the semver patch on every local build (assemble/bundle/install).
// Skipped in CI, where VERSION_NAME/VERSION_CODE are injected via env instead.
run {
    val isCi = !System.getenv("VERSION_NAME").isNullOrBlank()
    val buildRequested = gradle.startParameter.taskNames.any { name ->
        val task = name.substringAfterLast(':')
        task.startsWith("assemble") || task.startsWith("bundle") || task.startsWith("install")
    }
    if (!isCi && buildRequested && versionPropsFile.exists()) {
        val current = versionProps.getProperty("versionName")?.removePrefix("v") ?: "1.0.0"
        val parts = current.split(".").map { it.toIntOrNull() ?: 0 }.toMutableList()
        while (parts.size < 3) parts.add(0)
        parts[2] = parts[2] + 1
        val bumped = "${parts[0]}.${parts[1]}.${parts[2]}"
        versionProps.setProperty("versionName", bumped)
        val versionCode = versionProps.getProperty("versionCode") ?: "1"
        versionPropsFile.writeText(
            """
            |# Single source of truth for the app version.
            |# Read by composeApp/build.gradle.kts. CI overrides via env VERSION_NAME / VERSION_CODE
            |# (versionCode = github.run_number), so bump versionName here per release.
            |# Patch is auto-incremented on every local build.
            |versionName=$bumped
            |versionCode=$versionCode
            |""".trimMargin() + "\n"
        )
    }
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
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.google.code.scanner)
            implementation(libs.google.app.update.ktx)
            implementation(libs.coil.compose)
            // ARCore (Google Play Services for AR) + SceneView (Filament + Compose)
            // for the offline AR cellar screen. Android target only.
            implementation(libs.google.arcore)
            implementation(libs.sceneview.arsceneview)
        }
    }
}

android {
    namespace = "fr.geoking.vincent"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "fr.geoking.vincent"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI overrides these via env so Play always gets an increasing versionCode.
        versionCode = (System.getenv("VERSION_CODE") ?: versionProps.getProperty("versionCode") ?: "1").toInt()
        versionName = (System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: versionProps.getProperty("versionName") ?: "1.0").removePrefix("v")
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"")
        buildConfigField("String", "WEB_CLIENT_ID", "\"${secret("WEB_CLIENT_ID")}\"")
        // Feature flag for the ARCore "AR cellar" screen. Optional AR_ENABLED in
        // local.properties / CI env (true|false) flips it; defaults to enabled.
        buildConfigField("Boolean", "AR_ENABLED", secret("AR_ENABLED").ifBlank { "true" })
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Release signing is driven by env vars (set by the release workflow from
    // repository secrets). Absent locally → release stays unsigned, debug is fine.
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
            isMinifyEnabled = false
            if (keystorePath != null) {
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
}
