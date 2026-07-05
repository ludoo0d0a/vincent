package fr.geoking.vincent.data

import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.ai.GeminiClient
import fr.geoking.vincent.model.FlavorProfile
import fr.geoking.vincent.debug.HttpDebug
import kotlinx.coroutines.tasks.await
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.Region
import fr.geoking.vincent.model.Producer
import fr.geoking.vincent.model.Bottle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


/** Firebase ID + App Check tokens for Worker proxy routes (no grapeminds API key). */
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

/** True when an API key is missing or still the build placeholder. */
private fun String.isConfigured(): Boolean = isNotBlank() && this != "xxx"

/**
 * Open Food Facts lookup by barcode (free, open, no key). Coverage for wine is
 * partial and noisy, and it never carries vintage/price — so this only prefills
 * name/brand; the label photo (when available) and manual form complete the rest.
 */
private object OpenFoodFactsProvider : WineDataProvider {
    override val id = "openfoodfacts"
    override val displayName = "Open Food Facts"
    override val capabilities = setOf(ProviderCapability.BARCODE_SCAN)

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
            val status = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                HttpDebug.log(
                    label = displayName,
                    method = "GET",
                    url = urlStr,
                    statusCode = status,
                    responseBody = err,
                    durationMs = elapsed,
                )
                return@withContext null
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(
                label = displayName,
                method = "GET",
                url = urlStr,
                statusCode = status,
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
                source = displayName,
            )
        } catch (e: Exception) {
            HttpDebug.log(
                label = displayName,
                method = "GET",
                url = "https://world.openfoodfacts.org/api/v2/product/$ean.json",
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
            null
        }
    }
}

/**
 * grapeminds Public API v1 — wine-specific catalogue (wines, producers, regions)
 * with free-text search and, on Enterprise plans, AI label photo analysis. Auth via
 * an `Authorization: Bearer` token; `Accept-Language` selects the language for region
 * names/descriptions (de, en, es, fr, it, da). No barcode lookup and no per-wine price.
 *
 * Base: https://api.grapeminds.eu/public/v1
 * - GET  /wines/search?q={q}&limit={n}  (q min 3 chars) -> { data: [wine], meta: {...} }
 * - POST /photo/analyze  { photo: "data:image/jpeg;base64,...", max_results }
 *        -> { detected_labels: [...], candidates: [wine] }   (Enterprise only; 403 otherwise)
 * A wine object: { id, display_name, color, type, sub_type, producer:{id,name}, region:{id,name,country} }.
 */
private object GrapeMindsProvider : WineDataProvider {
    override val id = "grapeminds"
    override val displayName = "grapeminds"
    override val capabilities = setOf(
        ProviderCapability.TEXT_SEARCH,
        ProviderCapability.LABEL_SCAN,
        ProviderCapability.ENRICH,
        ProviderCapability.LIST_PRODUCERS,
        ProviderCapability.LIST_REGIONS,
        ProviderCapability.LIST_WINES,
    )

    private const val BASE = "https://api.grapeminds.eu/public/v1"
    private val PROXY_BASE = "${BuildConfig.AI_PROXY_URL}/v1/grapeminds"
    // Language for region names/descriptions; matches the supported set (de,en,es,fr,it,da).
    private const val LANG = "fr"

    override suspend fun search(query: String): List<ProductInfo> = withContext(Dispatchers.IO) {
        val key = BuildConfig.GRAPEMINDS_API_KEY
        if (!key.isConfigured()) return@withContext emptyList()
        val q = query.trim()
        if (q.length < 3) return@withContext emptyList() // API requires min 3 characters
        val urlStr = "$BASE/wines/search?q=${URLEncoder.encode(q, "UTF-8")}&limit=20"
        val resp = get(urlStr, key) ?: return@withContext emptyList()
        try {
            val rows = JSONObject(resp).optJSONArray("data") ?: return@withContext emptyList()
            (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toProductInfo() }
        } catch (e: Exception) {
            HttpDebug.log(label = displayName, method = "GET", url = urlStr, error = "${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    override suspend fun byLabel(imageBytes: ByteArray): ProductInfo? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GRAPEMINDS_API_KEY
        if (!key.isConfigured()) return@withContext null
        val urlStr = "$BASE/photo/analyze"
        val started = System.currentTimeMillis()
        try {
            val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val payload = JSONObject()
                .put("photo", "data:image/jpeg;base64,$b64")
                .put("max_results", 5)
                .toString()
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", LANG)
            }
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val status = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                // 403 = plan without Enterprise photo analysis; treat as "no result", not a crash.
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                HttpDebug.log(label = displayName, method = "POST", url = urlStr, statusCode = status, responseBody = err, durationMs = elapsed)
                return@withContext null
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(label = displayName, method = "POST", url = urlStr, requestBody = "<image ${imageBytes.size} bytes>", statusCode = status, responseBody = resp, durationMs = elapsed)
            val root = JSONObject(resp)
            // Prefer the structured detected label (carries vintage); fall back to the first candidate.
            val label = root.optJSONArray("detected_labels")?.optJSONObject(0)
            // The matched DB wine (when any) carries the id used for enrichment.
            val topCandidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val candidateId = topCandidate?.optInt("id", 0)?.takeIf { it > 0 }?.toString()
            if (label != null) {
                val name = label.str("wine_name")
                val producer = label.str("producer_name")
                if (name.isNotEmpty() || producer.isNotEmpty()) {
                    return@withContext ProductInfo(
                        name = name,
                        brand = producer,
                        country = label.str("country"),
                        category = label.str("color"),
                        vintage = label.optInt("vintage", 0).takeIf { it > 0 }?.toString(),
                        region = label.str("region_name").takeIf { it.isNotEmpty() },
                        source = displayName,
                        externalId = candidateId,
                        externalSource = if (candidateId != null) id else null,
                    )
                }
            }
            topCandidate?.toProductInfo()
        } catch (e: Exception) {
            HttpDebug.log(label = displayName, method = "POST", url = urlStr, error = "${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    override suspend fun listProducers(): List<Producer> = withContext(Dispatchers.IO) {
        fetchPaginated("$PROXY_BASE/producers", perPage = 100, maxPages = 10, key = "")
            .mapNotNull { o ->
                val name = o.str("name")
                if (name.isEmpty()) return@mapNotNull null
                val title = o.str("title")
                Producer(
                    id = "gm-${o.optInt("id")}",
                    name = if (title.isNotEmpty()) "$title $name" else name,
                    country = "",
                )
            }
    }

    override suspend fun listRegions(): List<Region> = withContext(Dispatchers.IO) {
        fetchPaginated("$PROXY_BASE/regions", perPage = 100, maxPages = 25, key = "")
            .mapNotNull { o ->
                val name = o.str("name")
                if (name.isEmpty()) return@mapNotNull null
                Region(
                    id = "gm-${o.optInt("id")}",
                    name = name,
                    country = o.str("country"),
                )
            }
    }

    override suspend fun listWines(): List<Bottle> = withContext(Dispatchers.IO) {
        fetchPaginated("$PROXY_BASE/wines", perPage = 100, maxPages = 5, key = "")
            .mapNotNull { o -> o.toImportBottle() }
    }

    override suspend fun enrich(externalId: String): WineEnrichment? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GRAPEMINDS_API_KEY
        if (!key.isConfigured()) return@withContext null
        val wineId = externalId.filter { it.isDigit() }
        if (wineId.isEmpty()) return@withContext null
        // /wines/{id}: descriptions, pairing, tasting notes, grapes.
        val wine = get("$BASE/wines/$wineId", key)?.let { runCatching { JSONObject(it) }.getOrNull() }
        // /drinking-periods/{id}: optimal drink window + maturity descriptions.
        val drink = get("$BASE/drinking-periods/$wineId?lang=$LANG", key)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (wine == null && drink == null) return@withContext null
        val grapes = wine?.optJSONArray("grapes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.str("name")?.takeIf { it.isNotEmpty() } }
        } ?: emptyList()
        val flavor = wine?.optJSONObject("flavor_profile")?.let { o ->
            FlavorProfile(
                sweetness = o.optInt("sweetness"),
                acidity = o.optInt("acidity"),
                tannins = o.optInt("tannins"),
                alcohol = o.optInt("alcohol"),
                body = o.optInt("body"),
                finish = o.optInt("finish"),
            )
        }
        WineEnrichment(
            // Raw from/to from API — offsets from vintage or absolute calendar years (see AddScreen).
            drinkFromYears = drink?.optInt("from", -1)?.takeIf { it >= 0 },
            drinkToYears = drink?.optInt("to", -1)?.takeIf { it >= 0 },
            maturity = drink?.str("statement") ?: "",
            young = drink?.str("young") ?: "",
            ripe = drink?.str("ripe") ?: "",
            storage = drink?.str("storage") ?: "",
            tastingNotes = wine?.optJSONObject("tasting_notes")?.str("text") ?: "",
            description = wine?.optJSONObject("description")?.str("text") ?: "",
            pairingText = wine?.optJSONObject("pairing")?.str("text") ?: "",
            grapes = grapes,
            flavorProfile = flavor,
            source = displayName,
        )
    }

    private suspend fun get(urlStr: String, key: String): String? {
        val started = System.currentTimeMillis()
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000

                // Use the provided API key if available (direct call).
                // Otherwise, use the app's session token for proxy calls.
                if (key.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $key")
                } else {
                    val (idToken, appCheckToken) = proxyAuthHeaders()
                    idToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                    appCheckToken?.let { setRequestProperty("X-Firebase-AppCheck", it) }
                }

                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", LANG)
            }
            val status = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                HttpDebug.log(label = displayName, method = "GET", url = urlStr, statusCode = status, responseBody = err, durationMs = elapsed)
                return null
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(label = displayName, method = "GET", url = urlStr, statusCode = status, responseBody = resp, durationMs = elapsed)
            resp
        } catch (e: Exception) {
            HttpDebug.log(label = displayName, method = "GET", url = urlStr, error = "${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Follows grapeminds pagination (`meta.last_page`) up to [maxPages]. */
    private suspend fun fetchPaginated(basePath: String, perPage: Int, maxPages: Int, key: String): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var page = 1
        while (page <= maxPages) {
            val sep = if (basePath.contains("?")) "&" else "?"
            val resp = get("$basePath${sep}per_page=$perPage&page=$page", key) ?: break
            val root = runCatching { JSONObject(resp) }.getOrNull() ?: break
            val data = root.optJSONArray("data") ?: break
            if (data.length() == 0) break
            for (i in 0 until data.length()) {
                data.optJSONObject(i)?.let { items.add(it) }
            }
            val lastPage = root.optJSONObject("meta")?.optInt("last_page", page) ?: page
            if (page >= lastPage) break
            page++
        }
        return items
    }

    /** Maps a grapeminds wine object (search row / photo candidate) to [ProductInfo]. */
    private fun JSONObject.toProductInfo(): ProductInfo? {
        val producer = optJSONObject("producer")
        val region = optJSONObject("region")
        // display_name is "Producer, Wine Region IGT"; producer.name is the cleaner brand.
        // /wines/search returns flat producer_name fields instead of a nested producer object.
        val name = str("display_name")
        val brand = producer?.str("name")
            ?: str("producer_name").ifBlank { str("producer_display_name") }
        if (name.isEmpty() && brand.isEmpty()) return null
        val wineId = optInt("id", 0).takeIf { it > 0 }?.toString()
        return ProductInfo(
            name = name,
            brand = brand,
            country = region?.str("country")?.uppercase() ?: str("country").uppercase(),
            category = str("color"), // red / white / rose
            region = region?.str("name")?.takeIf { it.isNotEmpty() } ?: str("region_name").takeIf { it.isNotEmpty() },
            source = displayName,
            externalId = wineId,
            externalSource = if (wineId != null) id else null,
        )
    }

    private fun JSONObject.toImportBottle(): Bottle? {
        val p = optJSONObject("producer")
        val r = optJSONObject("region")
        val domain = p?.str("name") ?: p?.str("display_name") ?: ""
        val appellation = str("display_name")
        if (domain.isEmpty() && appellation.isEmpty()) return null
        val subType = str("sub_type")
        val colorStr = str("color")
        return Bottle(
            id = "gm-${optInt("id")}",
            domain = domain,
            appellation = appellation,
            color = when {
                subType == "sparkling" -> WineColor.SPARKLING
                colorStr == "white" -> WineColor.WHITE
                colorStr == "rose" -> WineColor.ROSE
                else -> WineColor.RED
            },
            category = WineCategory.BORDEAUX,
            vintage = optInt("vintage", 0).takeIf { it > 0 }?.toString() ?: "NM",
            price = 0,
            quantity = 1,
            rating = 0.0,
            cellarSpot = "—",
            provenance = r?.str("name") ?: "",
            merchant = "—",
            purchaseDate = "",
            occasion = "",
        )
    }

    private fun JSONObject.str(key: String): String {
        val v = optString(key, "").trim()
        return if (v == "null") "" else v
    }
}

/**
 * Wikipedia provider — fetches wine regions from the proxy.
 */
private object WikipediaProvider : WineDataProvider {
    override val id = "wikipedia"
    override val displayName = "Wikipedia"
    override val capabilities = setOf(ProviderCapability.LIST_REGIONS)

    override suspend fun listRegions(): List<Region> = withContext(Dispatchers.IO) {
        val urlStr = "${BuildConfig.AI_PROXY_URL}/v1/scrape/wikipedia/regions"
        val started = System.currentTimeMillis()
        try {
            val (idToken, appCheckToken) = proxyAuthHeaders()
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                idToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                appCheckToken?.let { setRequestProperty("X-Firebase-AppCheck", it) }
                setRequestProperty("Accept", "application/json")
            }
            val status = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) return@withContext emptyList()
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(resp)
            val arr = root.optJSONArray("regions") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val name = arr.getString(i)
                Region(id = "wp-$name", name = name, country = "France")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * AI label reader — wraps the Gemini-backed recognizer as a LABEL_SCAN provider so
 * the factory can route label photos uniformly alongside the data sources.
 */
private object AiLabelProvider : WineDataProvider {
    override val id = "ai-label"
    override val displayName = "Vincent AI"
    override val capabilities = setOf(ProviderCapability.LABEL_SCAN)

    override suspend fun byLabel(imageBytes: ByteArray): ProductInfo? {
        val bottle = GeminiClient.fromImage(imageBytes).bottle ?: return null
        return ProductInfo(
            name = bottle.appellation.ifBlank { bottle.domain },
            brand = bottle.domain,
            country = "",
            category = bottle.category.name,
            vintage = bottle.vintage.takeIf { it.isNotBlank() && it != "NM" },
            region = bottle.provenance.takeIf { it.isNotBlank() },
            source = displayName,
        )
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

actual fun wineDataProviders(): List<WineDataProvider> = listOf(
    OpenFoodFactsProvider, // free barcode, no key — first
    GrapeMindsProvider,    // text search + AI label analysis (Enterprise) — grapeminds.eu
    AiLabelProvider,       // label photo recognition (AI)
    WikipediaProvider,     // region scraping
)
