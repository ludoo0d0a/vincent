package fr.geoking.vincent

import fr.geoking.vincent.data.Features

/** Global feature switches. Compile-time values come from BuildConfig / env. */
object FeatureFlags {
    /**
     * Cloud sync of cellar metadata (no photos) via Firestore when signed in with Google.
     * Driven by `CLOUD_SYNC` in local.properties / Gradle / env (default: false).
     */
    val CLOUD_SYNC: Boolean get() = Features.cloudSync
}
