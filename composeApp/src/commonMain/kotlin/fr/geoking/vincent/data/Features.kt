package fr.geoking.vincent.data

/**
 * Compile-time feature gates surfaced to commonMain. The Android `actual` reads
 * `BuildConfig` so a single flag can hide an entire feature from the UI.
 */
expect object Features {
    /** Master switch for the ARCore "AR cellar" feature (entry point + screen). */
    val arEnabled: Boolean

    /** Master switch for Firestore cloud sync when signed in with Google. */
    val cloudSync: Boolean
}
