package fr.geoking.vincent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

actual object Settings {
    private var prefs: SharedPreferences? = null
    private var securePrefs: SharedPreferences? = null

    // The device locale captured before we ever override it, so "system default"
    // can be restored faithfully.
    private var systemLocale: Locale = Locale.getDefault()

    private var _internalLogEnabled by mutableStateOf(false)
    actual val internalLogEnabled: Boolean get() = _internalLogEnabled

    private var _language by mutableStateOf("")
    actual val language: String get() = _language

    actual val currentLanguageTag: String
        get() = currentLocale().language.ifBlank { "fr" }

    private var _demoDataSeeded by mutableStateOf(false)
    actual val demoDataSeeded: Boolean get() = _demoDataSeeded

    private var _geminiApiKey by mutableStateOf("")
    actual val geminiApiKey: String get() = _geminiApiKey

    private var _geminiFallbackEnabled by mutableStateOf(false)
    actual val geminiFallbackEnabled: Boolean get() = _geminiFallbackEnabled

    private var _huggingFaceToken by mutableStateOf("")
    actual val huggingFaceToken: String get() = _huggingFaceToken

    fun init(context: Context) {
        systemLocale = Locale.getDefault()
        val p = context.getSharedPreferences("vincent_settings", Context.MODE_PRIVATE)
        prefs = p
        _internalLogEnabled = p.getBoolean("internal_log_enabled", false)
        _language = p.getString("language", "").orEmpty()
        _demoDataSeeded = p.getBoolean("demo_data_seeded", false)
        _geminiFallbackEnabled = p.getBoolean("gemini_fallback_enabled", false)

        securePrefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "vincent_secure_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Fallback if Keystore is unavailable (rare); still better than BuildConfig.
            context.getSharedPreferences("vincent_secure_settings_fallback", Context.MODE_PRIVATE)
        }
        _geminiApiKey = securePrefs?.getString("gemini_api_key", "").orEmpty()
        _huggingFaceToken = securePrefs?.getString("huggingface_token", "").orEmpty()
        applyLocale()
    }

    actual fun setInternalLogEnabled(enabled: Boolean) {
        _internalLogEnabled = enabled
        prefs?.edit()?.putBoolean("internal_log_enabled", enabled)?.apply()
    }

    actual fun setLanguage(tag: String) {
        _language = tag
        prefs?.edit()?.putString("language", tag)?.apply()
        applyLocale()
    }

    actual fun setDemoDataSeeded(seeded: Boolean) {
        _demoDataSeeded = seeded
        prefs?.edit()?.putBoolean("demo_data_seeded", seeded)?.apply()
    }

    actual fun setGeminiApiKey(key: String) {
        _geminiApiKey = key.trim()
        securePrefs?.edit()?.putString("gemini_api_key", _geminiApiKey)?.apply()
    }

    actual fun setGeminiFallbackEnabled(enabled: Boolean) {
        _geminiFallbackEnabled = enabled
        prefs?.edit()?.putBoolean("gemini_fallback_enabled", enabled)?.apply()
    }

    actual fun setHuggingFaceToken(token: String) {
        _huggingFaceToken = token.trim()
        securePrefs?.edit()?.putString("huggingface_token", _huggingFaceToken)?.apply()
    }

    /** The locale the app should currently use (forced choice or system default). */
    fun currentLocale(): Locale =
        if (_language.isBlank()) systemLocale else Locale.forLanguageTag(_language)

    // Drives both Compose resource lookup (via getSystemResourceEnvironment, which
    // reads Locale.getDefault) and the Gemini "respond in this language" directive.
    private fun applyLocale() {
        Locale.setDefault(currentLocale())
    }
}
