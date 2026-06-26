package fr.geoking.vincent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

actual object Settings {
    private var prefs: SharedPreferences? = null

    private var _internalLogEnabled by mutableStateOf(false)
    actual val internalLogEnabled: Boolean get() = _internalLogEnabled

    fun init(context: Context) {
        val p = context.getSharedPreferences("vincent_settings", Context.MODE_PRIVATE)
        prefs = p
        _internalLogEnabled = p.getBoolean("internal_log_enabled", false)
    }

    actual fun setInternalLogEnabled(enabled: Boolean) {
        _internalLogEnabled = enabled
        prefs?.edit()?.putBoolean("internal_log_enabled", enabled)?.apply()
    }
}
