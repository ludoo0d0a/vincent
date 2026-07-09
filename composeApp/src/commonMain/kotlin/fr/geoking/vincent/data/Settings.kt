package fr.geoking.vincent.data

/** A language the user can force the app into (empty tag = follow the system). */
data class AppLanguage(val tag: String, val nativeName: String)

/** Languages we ship translations for. The "system default" choice uses tag "". */
val SUPPORTED_LANGUAGES = listOf(
    AppLanguage("fr", "Français"),
    AppLanguage("en", "English"),
)

expect object Settings {
    val internalLogEnabled: Boolean
    fun setInternalLogEnabled(enabled: Boolean)

    /** BCP-47 tag of the forced language, or "" to follow the system locale. */
    val language: String
    fun setLanguage(tag: String)

    /** Primary language subtag (e.g. "fr", "en") for locale-aware network calls. */
    val currentLanguageTag: String

    val demoDataSeeded: Boolean
    fun setDemoDataSeeded(seeded: Boolean)
}
