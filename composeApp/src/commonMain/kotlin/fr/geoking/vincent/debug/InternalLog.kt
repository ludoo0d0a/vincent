package fr.geoking.vincent.debug

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class LogLevel {
    INFO, WARN, ERROR
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val throwable: Throwable? = null
)

object InternalLog {
    var enabled: Boolean
        get() = Settings.internalLogEnabled
        set(value) = Settings.setInternalLogEnabled(value)

    val entries = mutableStateListOf<LogEntry>()
    private const val MAX_ENTRIES = 500
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (!enabled) return
        scope.launch {
            entries.add(0, LogEntry(level, tag, message, throwable = throwable))
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(entries.lastIndex)
            }
        }
    }

    fun clear() {
        entries.clear()
    }
}
