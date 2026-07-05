package fr.geoking.vincent.data

import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.debug.HttpDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Pagination metadata returned by list endpoints (`meta` object). */
data class GmPageMeta(
    val currentPage: Int,
    val lastPage: Int,
    val perPage: Int,
    val total: Int,
)

data class GmPagedResponse<T>(
    val data: List<T>,
    val meta: GmPageMeta?,
)

data class GmGrapeRef(val id: Int, val name: String)

data class GmProducerRef(
    val id: Int,
    val name: String,
    val title: String = "",
    val displayName: String = "",
)

data class GmRegionRef(
    val id: Int,
    val name: String,
    val country: String = "",
    val language: String = "",
)

data class GmLocalizedText(val text: String, val textLong: String = "")

data class GmFlavorProfile(
    val sweetness: Int,
    val acidity: Int,
    val tannins: Int,
    val alcohol: Int,
    val body: Int,
    val finish: Int,
)

/** Summary row from `GET /wines` or search / photo candidates. */
data class GmWineSummary(
    val id: Int,
    val displayName: String,
    val color: String,
    val type: String = "wine",
    val subType: String = "still",
    val residualSugar: String? = null,
    val producer: GmProducerRef? = null,
    val region: GmRegionRef? = null,
    /** Flat fields present on `/wines/search` rows instead of nested producer. */
    val producerName: String = "",
    val producerTitle: String = "",
    val producerDisplayName: String = "",
    val vintage: Int? = null,
)

/** Full wine from `GET /wines/{id}`. */
data class GmWineDetail(
    val id: Int,
    val displayName: String,
    val color: String,
    val type: String,
    val subType: String,
    val residualSugar: String?,
    val producer: GmProducerRef?,
    val region: GmRegionRef?,
    val grapes: List<GmGrapeRef>,
    val description: GmLocalizedText?,
    val pairing: GmLocalizedText?,
    val tastingNotes: GmLocalizedText?,
    val flavorProfile: GmFlavorProfile?,
)

data class GmProducer(
    val id: Int,
    val name: String,
    val title: String = "",
    val displayName: String = "",
)

data class GmProducerDetail(
    val producer: GmProducer,
    val wines: List<GmWineSummary> = emptyList(),
)

data class GmRegion(
    val id: Int,
    val name: String,
    val country: String = "",
    val language: String = "",
)

data class GmRegionDetail(
    val region: GmRegion,
    val wines: List<GmWineSummary> = emptyList(),
)

/** `GET /region-insights/{regionId}` — climate, terroir, signature styles. */
data class GmRegionInsight(
    val id: Int,
    val regionId: Int,
    val lang: String,
    val summary: String,
    val climateAndTerroir: String,
    val signatureStyles: List<String>,
    val keyGrapes: List<GmGrapeRef>,
    val generating: Boolean = false,
)

/** `GET /drinking-periods/{wineId}` — optimal drink window + maturity notes. */
data class GmDrinkingPeriod(
    val id: Int,
    val wineId: Int,
    val lang: String,
    val from: Int,
    val to: Int,
    val statement: String,
    val young: String,
    val ripe: String,
    val storage: String,
    val generating: Boolean = false,
)

data class GmDetectedLabel(
    val wineName: String,
    val producerName: String,
    val regionName: String,
    val country: String,
    val color: String,
    val vintage: Int?,
)

data class GmPhotoAnalysis(
    val detectedLabels: List<GmDetectedLabel>,
    val candidates: List<GmWineSummary>,
)

/** Filters for `GET /wines`. */
data class GmWineListFilter(
    val color: String? = null,
    val subType: String? = null,
    val producerId: Int? = null,
    val regionId: Int? = null,
)

/**
 * Typed HTTP client for the grapeminds Public API v1.
 *
 * Base: `https://api.grapeminds.eu/public/v1`
 * Auth: `Authorization: Bearer {API_KEY}`; `Accept-Language` ∈ {de, en, es, fr, it, da}.
 *
 * Catalogue list endpoints (`/wines`, `/producers`, `/regions`) are routed through the
 * Cloudflare Worker proxy when [proxyBase] is set so the app API key stays server-side.
 * Search, detail, enrichment, photo analysis, region insights and drinking periods use
 * the direct API with [apiKey].
 */
class GrapeMindsClient(
    private val apiKey: String,
    private val language: String = DEFAULT_LANGUAGE,
    private val proxyBase: String? = null,
    private val authHeaders: suspend () -> Pair<String?, String?> = { null to null },
) {
    // ── Wines ───────────────────────────────────────────────────────────────

    suspend fun listWines(
        page: Int = 1,
        perPage: Int = 15,
        filter: GmWineListFilter = GmWineListFilter(),
    ): GmPagedResponse<GmWineSummary>? = withContext(Dispatchers.IO) {
        val q = buildQuery {
            add("page", page)
            add("per_page", perPage.coerceIn(1, MAX_PER_PAGE))
            filter.color?.let { add("color", it) }
            filter.subType?.let { add("sub_type", it) }
            filter.producerId?.let { add("producer_id", it) }
            filter.regionId?.let { add("region_id", it) }
        }
        getPagedViaProxy("/wines$q") { it.toWineSummary() }
    }

    suspend fun listAllWines(
        perPage: Int = 100,
        maxPages: Int = 5,
        filter: GmWineListFilter = GmWineListFilter(),
    ): List<GmWineSummary> = fetchAllPages(maxPages) { page ->
        listWines(page = page, perPage = perPage, filter = filter)
    }

    suspend fun searchWines(query: String, limit: Int = 20): List<GmWineSummary> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 3) return@withContext emptyList()
        val url = "$BASE_URL/wines/search?q=${encode(q)}&limit=${limit.coerceIn(1, MAX_PER_PAGE)}"
        val resp = getDirect(url) ?: return@withContext emptyList()
        runCatching {
            JSONObject(resp).optJSONArray("data")?.toWineSummaries() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getWine(wineId: Int): GmWineDetail? = withContext(Dispatchers.IO) {
        getDirect("$BASE_URL/wines/$wineId")?.let { runCatching { JSONObject(it).optJSONObject("data")?.toWineDetail() }.getOrNull() }
    }

    // ── Producers ─────────────────────────────────────────────────────────────

    suspend fun listProducers(
        page: Int = 1,
        perPage: Int = 15,
        search: String? = null,
    ): GmPagedResponse<GmProducer>? = withContext(Dispatchers.IO) {
        val q = buildQuery {
            add("page", page)
            add("per_page", perPage.coerceIn(1, MAX_PER_PAGE))
            search?.trim()?.takeIf { it.length >= 2 }?.let { add("search", it) }
        }
        getPagedViaProxy("/producers$q") { it.toProducer() }
    }

    suspend fun listAllProducers(perPage: Int = 100, maxPages: Int = 10, search: String? = null): List<GmProducer> =
        fetchAllPages(maxPages) { page -> listProducers(page = page, perPage = perPage, search = search) }

    suspend fun getProducer(producerId: Int, includeWines: Boolean = false): GmProducerDetail? =
        withContext(Dispatchers.IO) {
            val q = if (includeWines) "?include_wines=1" else ""
            getDirect("$BASE_URL/producers/$producerId$q")?.let {
                runCatching { JSONObject(it).optJSONObject("data")?.toProducerDetail() }.getOrNull()
            }
        }

    // ── Regions ───────────────────────────────────────────────────────────────

    suspend fun listRegions(
        page: Int = 1,
        perPage: Int = 15,
        country: String? = null,
        search: String? = null,
    ): GmPagedResponse<GmRegion>? = withContext(Dispatchers.IO) {
        val q = buildQuery {
            add("page", page)
            add("per_page", perPage.coerceIn(1, MAX_PER_PAGE))
            country?.trim()?.takeIf { it.isNotEmpty() }?.let { add("country", it) }
            search?.trim()?.takeIf { it.isNotEmpty() }?.let { add("search", it) }
        }
        getPagedViaProxy("/regions$q") { it.toRegion() }
    }

    suspend fun listAllRegions(
        perPage: Int = 100,
        maxPages: Int = 25,
        country: String? = null,
        search: String? = null,
    ): List<GmRegion> = fetchAllPages(maxPages) { page ->
        listRegions(page = page, perPage = perPage, country = country, search = search)
    }

    suspend fun getRegion(regionId: Int, includeWines: Boolean = false): GmRegionDetail? =
        withContext(Dispatchers.IO) {
            val q = if (includeWines) "?include_wines=1" else ""
            getDirect("$BASE_URL/regions/$regionId$q")?.let {
                runCatching { JSONObject(it).optJSONObject("data")?.toRegionDetail() }.getOrNull()
            }
        }

    // ── Region insights ───────────────────────────────────────────────────────

    suspend fun getRegionInsights(regionId: Int, lang: String = language): GmRegionInsight? =
        withContext(Dispatchers.IO) {
            val resp = getDirect("$BASE_URL/region-insights/$regionId?lang=${encode(lang)}") ?: return@withContext null
            runCatching { JSONObject(resp).toRegionInsight() }.getOrNull()
        }

    // ── Drinking periods ──────────────────────────────────────────────────────

    suspend fun getDrinkingPeriod(wineId: Int, lang: String = language): GmDrinkingPeriod? =
        withContext(Dispatchers.IO) {
            val resp = getDirect("$BASE_URL/drinking-periods/$wineId?lang=${encode(lang)}") ?: return@withContext null
            runCatching { JSONObject(resp).toDrinkingPeriod() }.getOrNull()
        }

    // ── Photo analysis (Enterprise) ───────────────────────────────────────────

    suspend fun analyzePhoto(imageBytes: ByteArray, maxResults: Int = 5): GmPhotoAnalysis? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/photo/analyze"
            val started = System.currentTimeMillis()
            try {
                val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                val payload = JSONObject()
                    .put("photo", "data:image/jpeg;base64,$b64")
                    .put("max_results", maxResults.coerceIn(1, 20))
                    .toString()
                val conn = openConnection(url, direct = true).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(payload.toByteArray()) }
                val status = conn.responseCode
                val elapsed = System.currentTimeMillis() - started
                if (status !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    HttpDebug.log(
                        label = LABEL, method = "POST", url = url,
                        statusCode = status, responseBody = err, durationMs = elapsed,
                    )
                    return@withContext null
                }
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                HttpDebug.log(
                    label = LABEL, method = "POST", url = url,
                    requestBody = "<image ${imageBytes.size} bytes>",
                    statusCode = status, responseBody = resp, durationMs = elapsed,
                )
                runCatching { JSONObject(resp).toPhotoAnalysis() }.getOrNull()
            } catch (e: Exception) {
                HttpDebug.log(label = LABEL, method = "POST", url = url, error = "${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private suspend fun getDirect(urlStr: String): String? {
        if (!apiKey.isConfigured()) return null
        return httpGet(urlStr, direct = true)
    }

    private suspend fun <T> getPagedViaProxy(
        path: String,
        parse: (JSONObject) -> T?,
    ): GmPagedResponse<T>? {
        val base = proxyBase?.takeIf { it.isNotBlank() } ?: return null
        val resp = httpGet("$base$path", direct = false) ?: return null
        return runCatching {
            val root = JSONObject(resp)
            val data: List<T> = root.optJSONArray("data")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let(parse)?.let { add(it) }
                    }
                }
            } ?: emptyList()
            GmPagedResponse(data = data, meta = root.optJSONObject("meta")?.toPageMeta())
        }.getOrNull()
    }

    private suspend fun httpGet(urlStr: String, direct: Boolean): String? {
        val started = System.currentTimeMillis()
        return try {
            val conn = openConnection(urlStr, direct).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val status = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                HttpDebug.log(
                    label = LABEL, method = "GET", url = urlStr,
                    statusCode = status, responseBody = err, durationMs = elapsed,
                )
                return null
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(
                label = LABEL, method = "GET", url = urlStr,
                statusCode = status, responseBody = resp, durationMs = elapsed,
            )
            resp
        } catch (e: Exception) {
            HttpDebug.log(label = LABEL, method = "GET", url = urlStr, error = "${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun openConnection(urlStr: String, direct: Boolean): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection)
        if (direct) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept-Language", language)
        } else {
            val (idToken, appCheckToken) = authHeaders()
            idToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            appCheckToken?.let { conn.setRequestProperty("X-Firebase-AppCheck", it) }
            conn.setRequestProperty("Accept-Language", language)
        }
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private suspend fun <T> fetchAllPages(
        maxPages: Int,
        fetchPage: suspend (Int) -> GmPagedResponse<T>?,
    ): List<T> {
        val items = mutableListOf<T>()
        var page = 1
        while (page <= maxPages) {
            val response = fetchPage(page) ?: break
            if (response.data.isEmpty()) break
            items.addAll(response.data)
            val lastPage = response.meta?.lastPage ?: page
            if (page >= lastPage) break
            page++
        }
        return items
    }

    companion object {
        const val BASE_URL = "https://api.grapeminds.eu/public/v1"
        const val DEFAULT_LANGUAGE = "fr"
        const val MAX_PER_PAGE = 100
        val SUPPORTED_LANGUAGES = setOf("de", "en", "es", "fr", "it", "da")
        private const val LABEL = "grapeminds"

        fun isConfigured(key: String = BuildConfig.GRAPEMINDS_API_KEY): Boolean =
            key.isNotBlank() && key != "xxx"

        fun fromBuildConfig(
            apiKey: String = BuildConfig.GRAPEMINDS_API_KEY,
            language: String = DEFAULT_LANGUAGE,
            proxyBase: String = "${BuildConfig.AI_PROXY_URL}/v1/grapeminds",
            authHeaders: suspend () -> Pair<String?, String?> = { null to null },
        ): GrapeMindsClient = GrapeMindsClient(apiKey, language, proxyBase, authHeaders)
    }
}

private fun String.isConfigured(): Boolean = isNotBlank() && this != "xxx"

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

private class QueryBuilder {
    private val parts = mutableListOf<String>()
    fun add(key: String, value: Any) {
        parts += "${encode(key)}=${encode(value.toString())}"
    }
    fun build(): String = if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
}

private inline fun buildQuery(block: QueryBuilder.() -> Unit): String =
    QueryBuilder().apply(block).build()

// ── JSON parsing ──────────────────────────────────────────────────────────────

private fun JSONObject.str(key: String): String {
    val v = optString(key, "").trim()
    return if (v == "null") "" else v
}

private fun JSONObject.toPageMeta(): GmPageMeta = GmPageMeta(
    currentPage = optInt("current_page", 1),
    lastPage = optInt("last_page", 1),
    perPage = optInt("per_page", 15),
    total = optInt("total", 0),
)

private fun JSONObject.toLocalizedText(): GmLocalizedText? {
    val text = str("text")
    if (text.isEmpty()) return null
    return GmLocalizedText(text = text, textLong = str("text_long"))
}

private fun JSONObject.toFlavorProfile(): GmFlavorProfile? {
    if (!has("sweetness")) return null
    return GmFlavorProfile(
        sweetness = optInt("sweetness"),
        acidity = optInt("acidity"),
        tannins = optInt("tannins"),
        alcohol = optInt("alcohol"),
        body = optInt("body"),
        finish = optInt("finish"),
    )
}

private fun JSONObject.toProducerRef(): GmProducerRef? {
    val name = str("name")
    if (name.isEmpty() && !has("id")) return null
    return GmProducerRef(
        id = optInt("id"),
        name = name,
        title = str("title"),
        displayName = str("display_name"),
    )
}

private fun JSONObject.toRegionRef(): GmRegionRef? {
    val name = str("name")
    if (name.isEmpty() && !has("id")) return null
    return GmRegionRef(
        id = optInt("id"),
        name = name,
        country = str("country"),
        language = str("language"),
    )
}

private fun JSONObject.toGrapeRef(): GmGrapeRef? {
    val name = str("name")
    if (name.isEmpty()) return null
    return GmGrapeRef(id = optInt("id"), name = name)
}

private fun JSONArray.toGrapeRefs(): List<GmGrapeRef> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.toGrapeRef() }

fun JSONObject.toWineSummary(): GmWineSummary? {
    val displayName = str("display_name")
    val producer = optJSONObject("producer")?.toProducerRef()
    val flatProducer = str("producer_name")
    if (displayName.isEmpty() && producer == null && flatProducer.isEmpty()) return null
    return GmWineSummary(
        id = optInt("id"),
        displayName = displayName,
        color = str("color"),
        type = str("type").ifEmpty { "wine" },
        subType = str("sub_type").ifEmpty { "still" },
        residualSugar = optString("residual_sugar").takeIf { it.isNotBlank() && it != "null" },
        producer = producer,
        region = optJSONObject("region")?.toRegionRef(),
        producerName = flatProducer,
        producerTitle = str("producer_title"),
        producerDisplayName = str("producer_display_name"),
        vintage = optInt("vintage", 0).takeIf { it > 0 },
    )
}

private fun JSONArray.toWineSummaries(): List<GmWineSummary> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.toWineSummary() }

private fun JSONObject.toWineDetail(): GmWineDetail? {
    val summary = toWineSummary() ?: return null
    return GmWineDetail(
        id = summary.id,
        displayName = summary.displayName,
        color = summary.color,
        type = summary.type,
        subType = summary.subType,
        residualSugar = summary.residualSugar,
        producer = summary.producer,
        region = summary.region,
        grapes = optJSONArray("grapes")?.toGrapeRefs() ?: emptyList(),
        description = optJSONObject("description")?.toLocalizedText(),
        pairing = optJSONObject("pairing")?.toLocalizedText(),
        tastingNotes = optJSONObject("tasting_notes")?.toLocalizedText(),
        flavorProfile = optJSONObject("flavor_profile")?.toFlavorProfile(),
    )
}

private fun JSONObject.toProducer(): GmProducer? {
    val name = str("name")
    if (name.isEmpty()) return null
    val title = str("title")
    return GmProducer(
        id = optInt("id"),
        name = name,
        title = title,
        displayName = str("display_name").ifEmpty { if (title.isNotEmpty()) "$title $name" else name },
    )
}

private fun JSONObject.toProducerDetail(): GmProducerDetail? {
    val producer = toProducer() ?: return null
    val wines = optJSONArray("wines")?.toWineSummaries() ?: emptyList()
    return GmProducerDetail(producer = producer, wines = wines)
}

private fun JSONObject.toRegion(): GmRegion? {
    val name = str("name")
    if (name.isEmpty()) return null
    return GmRegion(
        id = optInt("id"),
        name = name,
        country = str("country"),
        language = str("language"),
    )
}

private fun JSONObject.toRegionDetail(): GmRegionDetail? {
    val region = toRegion() ?: return null
    val wines = optJSONArray("wines")?.toWineSummaries() ?: emptyList()
    return GmRegionDetail(region = region, wines = wines)
}

private fun JSONObject.toRegionInsight(): GmRegionInsight? {
    if (has("error") && !has("summary")) {
        return GmRegionInsight(
            id = 0,
            regionId = optInt("region_id"),
            lang = str("lang"),
            summary = "",
            climateAndTerroir = "",
            signatureStyles = emptyList(),
            keyGrapes = emptyList(),
            generating = optBoolean("generating", false),
        )
    }
    val summary = str("summary")
    if (summary.isEmpty() && !optBoolean("generating", false)) return null
    val styles = optJSONArray("signature_styles")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotEmpty() } }
    } ?: emptyList()
    return GmRegionInsight(
        id = optInt("id"),
        regionId = optInt("region_id"),
        lang = str("lang"),
        summary = summary,
        climateAndTerroir = str("climate_and_terroir"),
        signatureStyles = styles,
        keyGrapes = optJSONArray("key_grapes")?.toGrapeRefs() ?: emptyList(),
        generating = optBoolean("generating", false),
    )
}

private fun JSONObject.toDrinkingPeriod(): GmDrinkingPeriod? {
    if (has("error") && !has("from")) {
        return GmDrinkingPeriod(
            id = 0,
            wineId = optInt("wine_id"),
            lang = str("lang"),
            from = -1,
            to = -1,
            statement = "",
            young = "",
            ripe = "",
            storage = "",
            generating = optBoolean("generating", false),
        )
    }
    if (!has("from") && !has("to")) return null
    return GmDrinkingPeriod(
        id = optInt("id"),
        wineId = optInt("wine_id"),
        lang = str("lang"),
        from = optInt("from", -1),
        to = optInt("to", -1),
        statement = str("statement"),
        young = str("young"),
        ripe = str("ripe"),
        storage = str("storage"),
        generating = optBoolean("generating", false),
    )
}

private fun JSONObject.toPhotoAnalysis(): GmPhotoAnalysis {
    val labels = optJSONArray("detected_labels")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                GmDetectedLabel(
                    wineName = o.str("wine_name"),
                    producerName = o.str("producer_name"),
                    regionName = o.str("region_name"),
                    country = o.str("country"),
                    color = o.str("color"),
                    vintage = o.optInt("vintage", 0).takeIf { it > 0 },
                )
            }
        }
    } ?: emptyList()
    val candidates = optJSONArray("candidates")?.toWineSummaries() ?: emptyList()
    return GmPhotoAnalysis(detectedLabels = labels, candidates = candidates)
}
