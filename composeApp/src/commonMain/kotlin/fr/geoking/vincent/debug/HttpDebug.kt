package fr.geoking.vincent.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class HttpLogEntry(
    val id: Long,
    val timestampMs: Long,
    val label: String,
    val method: String,
    val url: String,
    val requestBody: String?,
    val statusCode: Int?,
    val responseBody: String?,
    val durationMs: Long,
    val error: String?,
)

/** In-memory ring buffer of recent HTTP calls — for the floating debug bar. */
object HttpDebug {
    var enabled by mutableStateOf(false)
    var apiKeyHint by mutableStateOf<String?>(null)

    val entries = mutableStateListOf<HttpLogEntry>()
    private var nextId = 0L

    const val MAX_ENTRIES = 40

    fun redactUrl(url: String): String =
        url.replace(Regex("([?&]key=)[^&]+", RegexOption.IGNORE_CASE), "$1***")

    fun truncate(text: String?, max: Int = 4000): String? {
        if (text == null) return null
        return if (text.length <= max) text else text.take(max) + "… (${text.length} car.)"
    }

    fun log(
        label: String,
        method: String,
        url: String,
        requestBody: String? = null,
        statusCode: Int? = null,
        responseBody: String? = null,
        durationMs: Long = 0,
        error: String? = null,
    ) {
        if (!enabled) return
        entries.add(
            0,
            HttpLogEntry(
                id = nextId++,
                timestampMs = System.currentTimeMillis(),
                label = label,
                method = method,
                url = redactUrl(url),
                requestBody = truncate(requestBody),
                statusCode = statusCode,
                responseBody = truncate(responseBody),
                durationMs = durationMs,
                error = error,
            ),
        )
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
    }

    fun clear() {
        entries.clear()
    }
}
