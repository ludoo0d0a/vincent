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

    /**
     * Optional Gemini API key entered by the user (BYOK). Empty → Gemini disabled.
     * Stored encrypted on Android.
     */
    val geminiApiKey: String
    fun setGeminiApiKey(key: String)

    /** When true and [geminiApiKey] is set, Gemini is used after Gemma fails. */
    val geminiFallbackEnabled: Boolean
    fun setGeminiFallbackEnabled(enabled: Boolean)

    /**
     * Optional Hugging Face token for downloading the gated Gemma `.task` file.
     * Not needed when a public GEMMA_MODEL_URL CDN is configured at build time.
     */
    val huggingFaceToken: String
    fun setHuggingFaceToken(token: String)
}
