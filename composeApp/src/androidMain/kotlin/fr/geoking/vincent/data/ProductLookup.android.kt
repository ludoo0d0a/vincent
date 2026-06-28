package fr.geoking.vincent.data

import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.ai.AiNetwork
import fr.geoking.vincent.debug.HttpDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open Food Facts lookup by barcode (free, open, no key). Coverage for wine is
 * partial and noisy, and it never carries vintage/price — so this only prefills
 * name/brand; the label photo (when available) and manual form complete the rest.
 */
private object OpenFoodFacts : BarcodeLookup {
    override suspend fun byBarcode(code: String): ProductInfo? = withContext(Dispatchers.IO) {
        val ean = code.filter { it.isDigit() }
        if (ean.length < 6) return@withContext null
        try {
            val urlStr =
                "https://world.openfoodfacts.org/api/v2/product/$ean.json" +
                    "?fields=product_name,brands,categories,countries," +
                    "image_front_url,image_front_small_url,selected_images"
            val started = System.currentTimeMillis()
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Vincent/1.0 (cave a vin; Android)")
            }
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                HttpDebug.log(
                    label = "OpenFoodFacts",
                    method = "GET",
                    url = urlStr,
                    statusCode = code,
                    responseBody = err,
                    durationMs = elapsed,
                )
                return@withContext null
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(
                label = "OpenFoodFacts",
                method = "GET",
                url = urlStr,
                statusCode = code,
                responseBody = resp,
                durationMs = elapsed,
            )
            val root = JSONObject(resp)
            if (root.optInt("status") != 1) return@withContext null
            val p = root.optJSONObject("product") ?: return@withContext null
            val name = p.optString("product_name").trim()
            val brand = p.optString("brands").trim()
            if (name.isEmpty() && brand.isEmpty()) return@withContext null
            ProductInfo(
                name = name,
                brand = brand,
                country = p.optString("countries").trim(),
                category = p.optString("categories").trim(),
                imageUrl = p.frontImageUrl(),
            )
        } catch (e: Exception) {
            HttpDebug.log(
                label = "OpenFoodFacts",
                method = "GET",
                url = "https://world.openfoodfacts.org/api/v2/product/$ean.json",
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
            null
        }
    }
}

/** Picks the best front-of-pack image URL from an OFF product payload. */
private fun JSONObject.frontImageUrl(): String? {
    listOf("image_front_url", "image_front_small_url").forEach { key ->
        optString(key).trim().takeIf { it.isNotEmpty() }?.let { return it }
    }
    val front = optJSONObject("selected_images")?.optJSONObject("front") ?: return null
    listOf("display", "small", "thumb").forEach { size ->
        val byLang = front.optJSONObject(size) ?: return@forEach
        listOf("fr", "en").forEach { lang ->
            byLang.optString(lang).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        byLang.keys().asSequence()
            .mapNotNull { byLang.optString(it).trim().takeIf { url -> url.isNotEmpty() } }
            .firstOrNull()
            ?.let { return it }
    }
    return null
}

/** Grapeminds-backed barcode lookup. */
private object GrapemindsBarcodeLookup : BarcodeLookup {
    override suspend fun byBarcode(code: String): ProductInfo? = withContext(Dispatchers.IO) {
        val prompt = "identify barcode: $code"
        val proxyUrl = BuildConfig.AI_PROXY_URL
        val json = if (proxyUrl.isNotBlank()) {
            val url = AiNetwork.grapemindsProxyUrl(proxyUrl)
            val body = JSONObject().put("prompt", prompt)
            AiNetwork.callProxy(url, body, label = "Grapeminds Barcode Proxy")
        } else {
            val apiKey = BuildConfig.GRAPEMINDS_API_KEY
            val apiUrl = BuildConfig.GRAPEMINDS_API_URL
            if (apiKey.isBlank() || apiUrl.isBlank()) null
            else {
                val body = JSONObject().put("prompt", prompt)
                val headers = mapOf("X-Api-Key" to apiKey, "Authorization" to "Bearer $apiKey")
                AiNetwork.callDirect(apiUrl, body, label = "Grapeminds Barcode Direct", headers = headers)
            }
        } ?: return@withContext null

        ProductInfo(
            name = json.optString("name").ifBlank { json.optString("domain") },
            brand = json.optString("brand").ifBlank { json.optString("domain") },
            country = json.optString("country").ifBlank { json.optString("region") },
            category = json.optString("category"),
            imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() },
        )
    }
}

actual fun barcodeLookup(): BarcodeLookup = if (BuildConfig.AI_PROVIDER == "grapeminds") GrapemindsBarcodeLookup else OpenFoodFacts
