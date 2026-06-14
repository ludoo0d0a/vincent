package fr.geoking.vincent

/** Global feature switches. Flip these as features get fully wired. */
object FeatureFlags {
    /**
     * Cloud sync of the cellar/favourites to the Google account is NOT wired yet
     * (data lives only in local Room). Kept false so the UI never promises a
     * capability the app doesn't have. Flip to true once real sync ships.
     */
    const val CLOUD_SYNC: Boolean = false
}
