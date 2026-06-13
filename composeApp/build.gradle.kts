import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}

// Secrets read from local.properties (or CI env), surfaced via BuildConfig — never hardcoded.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = localProps.getProperty(key) ?: System.getenv(key) ?: ""

// Version is managed in playstore/version.properties (CI env overrides it).
val versionProps = Properties().apply {
    val f = rootProject.file("playstore/version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

kotlin {
    jvmToolchain(17)

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.identity.googleid)
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Room annotation processing for the Android target (entities/DAO live in androidMain).
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
