package fr.geoking.vincent

expect fun getAppVersion(): String

/** Locale-aware short date/time for sync status labels. */
expect fun formatShortDateTime(epochMs: Long): String

expect fun getCurrentYear(): Int
