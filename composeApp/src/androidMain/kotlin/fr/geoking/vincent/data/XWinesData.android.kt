package fr.geoking.vincent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.db.XWineDao
import fr.geoking.vincent.db.XWineEntity
import fr.geoking.vincent.debug.HttpDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

actual object XWinesData {

    private const val PREFS = "vincent_settings"
    private const val KEY_UPDATED_AT = "xwines_updated_at"
    private const val LABEL = "X-Wines"

    private var prefs: SharedPreferences? = null
    private var dao: XWineDao? = null

    private var _updatedAt by mutableLongStateOf(0L)
    actual val updatedAt: Long get() = _updatedAt

    actual val updatedAtLabel: String
        get() = if (_updatedAt <= 0L) {
            ""
        } else {
            java.text.DateFormat
                .getDateInstance(java.text.DateFormat.MEDIUM)
                .format(java.util.Date(_updatedAt))
        }

    private var _count by mutableIntStateOf(0)
    actual val count: Int get() = _count

    private var _isDownloading by mutableStateOf(false)
    actual val isDownloading: Boolean get() = _isDownloading

    fun init(context: Context, xWineDao: XWineDao) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        dao = xWineDao
        _updatedAt = p.getLong(KEY_UPDATED_AT, 0L)
        // Load the current row count off the main thread; cheap query.
        MainScope().launch {
            _count = runCatching { xWineDao.count() }.getOrDefault(0)
        }
    }

    actual suspend fun update(): Result<Int> {
        val dao = dao ?: return Result.failure(IllegalStateException("XWinesData not initialized"))
        val url = BuildConfig.X_WINES_DATASET_URL
        if (url.isBlank() || url == "xxx") {
            return Result.failure(IllegalStateException("X_WINES_DATASET_URL not configured"))
        }
        _isDownloading = true
        return try {
            withContext(Dispatchers.IO) {
                val text = fetch(url)
                val wines = parse(text)
                if (wines.isEmpty()) {
                    return@withContext Result.failure<Int>(IllegalStateException("Empty dataset"))
                }
                dao.clear()
                wines.chunked(2000).forEach { dao.insertAll(it) }
                val now = System.currentTimeMillis()
                prefs?.edit()?.putLong(KEY_UPDATED_AT, now)?.apply()
                _updatedAt = now
                _count = wines.size
                Result.success(wines.size)
            }
        } catch (e: Exception) {
            HttpDebug.log(label = LABEL, method = "GET", url = url, error = "${e.javaClass.simpleName}: ${e.message}")
            Result.failure(e)
        } finally {
            _isDownloading = false
        }
    }

    private fun fetch(urlStr: String): String {
        val started = System.currentTimeMillis()
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "Vincent/1.0 (cave a vin; Android)")
        }
        val code = conn.responseCode
        val elapsed = System.currentTimeMillis() - started
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            HttpDebug.log(label = LABEL, method = "GET", url = urlStr, statusCode = code, responseBody = err, durationMs = elapsed)
            throw IllegalStateException("HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        HttpDebug.log(label = LABEL, method = "GET", url = urlStr, statusCode = code, responseBody = "<${body.length} chars>", durationMs = elapsed)
        return body
    }

    /** Accepts a JSON array of objects or a CSV with a header row. */
    private fun parse(text: String): List<XWineEntity> {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[")) parseJson(trimmed) else parseCsv(text)
    }

    private fun parseJson(text: String): List<XWineEntity> {
        val arr = JSONArray(text)
        val out = ArrayList<XWineEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.firstLong("WineID", "id", "wine_id") ?: i.toLong()
            out += XWineEntity(
                id = id,
                name = o.firstString("WineName", "name", "wine_name"),
                type = o.firstString("Type", "type", "Elaborate"),
                grapes = o.firstString("Grapes", "grapes"),
                country = o.firstString("Country", "country"),
                region = o.firstString("RegionName", "region", "Region"),
                winery = o.firstString("WineryName", "winery", "Winery"),
            )
        }
        return out
    }

    private fun parseCsv(text: String): List<XWineEntity> {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()
        val header = splitCsv(lines.next()).map { it.trim().lowercase() }
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val iId = idx("wineid", "id", "wine_id")
        val iName = idx("winename", "name", "wine_name")
        val iType = idx("type", "elaborate")
        val iGrapes = idx("grapes")
        val iCountry = idx("country")
        val iRegion = idx("regionname", "region")
        val iWinery = idx("wineryname", "winery")
        val out = ArrayList<XWineEntity>()
        var auto = 0L
        for (line in lines) {
            if (line.isBlank()) continue
            val cols = splitCsv(line)
            fun col(i: Int) = if (i in cols.indices) cols[i].trim() else ""
            out += XWineEntity(
                id = col(iId).toLongOrNull() ?: auto++,
                name = col(iName),
                type = col(iType),
                grapes = col(iGrapes),
                country = col(iCountry),
                region = col(iRegion),
                winery = col(iWinery),
            )
        }
        return out
    }

    // Minimal CSV splitter handling quoted fields with embedded commas/quotes.
    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun JSONObject.firstString(vararg keys: String): String {
        for (k in keys) {
            val v = optString(k, "").trim()
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    private fun JSONObject.firstLong(vararg keys: String): Long? {
        for (k in keys) {
            if (has(k)) {
                val v = optLong(k, Long.MIN_VALUE)
                if (v != Long.MIN_VALUE) return v
            }
        }
        return null
    }

    /** Text search over the local dataset, used by the X-Wines provider. */
    suspend fun search(query: String): List<XWineEntity> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return runCatching { dao?.search(q) ?: emptyList() }.getOrDefault(emptyList())
    }
}
