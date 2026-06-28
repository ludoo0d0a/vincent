package fr.geoking.vincent.data

/**
 * The embedded X-Wines dataset (free, open). It is not bundled with the APK:
 * the user downloads/updates it on demand from [BuildConfig-provided URL], and it
 * is stored locally for offline text search by the X-Wines [WineDataProvider].
 *
 * State is observable (Compose snapshot state) so the settings UI can reflect the
 * last update time, the row count, and an in-flight download.
 */
expect object XWinesData {
    /** Epoch millis of the last successful download, or 0 if never. */
    val updatedAt: Long

    /** Localized date label for [updatedAt], or empty when never downloaded. */
    val updatedAtLabel: String

    /** Number of wines currently stored locally. */
    val count: Int

    /** True while a download/update is in progress. */
    val isDownloading: Boolean

    /** Downloads and replaces the local dataset. Returns the imported row count. */
    suspend fun update(): Result<Int>
}
