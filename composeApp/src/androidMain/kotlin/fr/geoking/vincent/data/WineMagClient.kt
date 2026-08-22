package fr.geoking.vincent.data

import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.debug.HttpDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Client for the WineMag archive search route on the Vincent Worker (D1-backed). */
object WineMagClient {

    fun isConfigured(): Boolean = BuildConfig.AI_PROXY_URL.isNotBlank()

    suspend fun search(query: String, limit: Int = 20): List<ProductInfo> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val base = BuildConfig.AI_PROXY_URL.trim().removeSuffix("/")
        val url = "$base/v1/catalog/search?q=${URLEncoder.encode(q, "UTF-8")}&limit=$limit"
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                proxyAuthHeaders().let { (id, appCheck) ->
                    appCheck?.let { setRequestProperty("X-Firebase-AppCheck", it) }
                    id?.let { setRequestProperty("Authorization", "Bearer $it") }
                }
            }
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log("WineMag", "GET", url, responseBody = body.take(500))
            parseSearchResponse(body)
        } catch (e: Exception) {
            HttpDebug.log("WineMag", "GET", url, error = "${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private suspend fun proxyAuthHeaders(): Pair<String?, String?> {
        val appCheckToken = try {
            FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        } catch (_: Exception) {
            null
        }
        val idToken = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) {
            null
        }
        return idToken to appCheckToken
    }

    private fun parseSearchResponse(json: String): List<ProductInfo> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val row = data.optJSONObject(i) ?: continue
                val title = row.optString("title").trim()
                val winery = row.optString("winery").trim()
                if (title.isEmpty() && winery.isEmpty()) continue
                add(
                    ProductInfo(
                        name = title.ifBlank { winery },
                        brand = winery,
                        country = row.optString("country").trim(),
                        category = row.optString("color").trim().ifBlank { "red" },
                        vintage = null,
                        grape = row.optString("variety").trim().takeIf { it.isNotEmpty() },
                        region = row.optString("region_1").trim().takeIf { it.isNotEmpty() },
                        source = "WineMag archive",
                        externalId = null,
                        externalSource = null,
                    ),
                )
            }
        }
    }
}
