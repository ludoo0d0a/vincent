package fr.geoking.vincent

/** Global feature switches. Flip these as features get fully wired. */
object FeatureFlags {
    /** Cloud sync of cellar metadata (no photos) via Firestore when signed in with Google. */
    const val CLOUD_SYNC: Boolean = true
}
