package fr.geoking.vincent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open Food Facts lookup by barcode (free, open, no key). Coverage for wine is
 * partial and noisy, and it never carries vintage/price — so this only prefills
 * name/brand; the label photo (AI) and manual form complete the rest.
 */
private object OpenFoodFacts : BarcodeLookup {
    override suspend fun byBarcode(code: String): ProductInfo? = withContext(Dispatchers.IO) {
        val ean = code.filter { it.isDigit() }
        if (ean.length < 6) return@withContext null
        try {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/$ean.json" +
                    "?fields=product_name,brands,categories,countries",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Vincent/1.0 (cave a vin; Android)")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
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
            )
        } catch (e: Exception) {
            null
        }
    }
}

actual fun barcodeLookup(): BarcodeLookup = OpenFoodFacts
