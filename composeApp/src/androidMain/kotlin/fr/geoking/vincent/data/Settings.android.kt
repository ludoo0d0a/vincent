package fr.geoking.vincent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

actual object Settings {
    private var prefs: SharedPreferences? = null

    // The device locale captured before we ever override it, so "system default"
    // can be restored faithfully.
    private var systemLocale: Locale = Locale.getDefault()

    private var _internalLogEnabled by mutableStateOf(false)
    actual val internalLogEnabled: Boolean get() = _internalLogEnabled

    private var _language by mutableStateOf("")
    actual val language: String get() = _language

    private var _demoDataSeeded by mutableStateOf(false)
    actual val demoDataSeeded: Boolean get() = _demoDataSeeded

    fun init(context: Context) {
        systemLocale = Locale.getDefault()
        val p = context.getSharedPreferences("vincent_settings", Context.MODE_PRIVATE)
        prefs = p
        _internalLogEnabled = p.getBoolean("internal_log_enabled", false)
        _language = p.getString("language", "").orEmpty()
        _demoDataSeeded = p.getBoolean("demo_data_seeded", false)
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

    /** The locale the app should currently use (forced choice or system default). */
    fun currentLocale(): Locale =
        if (_language.isBlank()) systemLocale else Locale.forLanguageTag(_language)

    // Drives both Compose resource lookup (via getSystemResourceEnvironment, which
    // reads Locale.getDefault) and the Gemini "respond in this language" directive.
    private fun applyLocale() {
        Locale.setDefault(currentLocale())
    }
}
