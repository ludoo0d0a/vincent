pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle auto-provision the requested JVM toolchain (build + daemon).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Vincent"
include(":composeApp")

val gkToolsRoot = System.getenv("GK_TOOLS")
    ?: listOf("geoking-tools", "../geoking-tools", "../../geoking-tools")
        .map { rootDir.resolve(it) }
        .firstOrNull { it.resolve("android").isDirectory }
        ?.absolutePath
        ?: error("geoking-tools not found; clone sibling or set GK_TOOLS")

includeBuild("$gkToolsRoot/android") {
    dependencySubstitution {
        substitute(module("fr.geoking.tools:in-app-update"))
            .using(project(":in-app-update"))
    }
}
