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
 * grapeminds Public API v1 — wine catalogue provider backed by [GrapeMindsClient].
 * See [GrapeMindsClient] for the full endpoint surface (wines, producers, regions,
 * region insights, drinking periods, photo analysis).
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

    private val client by lazy {
        GrapeMindsClient.fromBuildConfig(authHeaders = ::proxyAuthHeaders)
    }

    override suspend fun search(query: String): List<ProductInfo> {
        if (!GrapeMindsClient.isConfigured()) return emptyList()
        return client.searchWines(query, limit = 20).mapNotNull { it.toProductInfo() }
    }

    override suspend fun byLabel(imageBytes: ByteArray): ProductInfo? {
        if (!GrapeMindsClient.isConfigured()) return null
        val analysis = client.analyzePhoto(imageBytes, maxResults = 5) ?: return null
        val label = analysis.detectedLabels.firstOrNull()
        val topCandidate = analysis.candidates.firstOrNull()
        val candidateId = topCandidate?.id?.takeIf { it > 0 }?.toString()
        if (label != null && (label.wineName.isNotEmpty() || label.producerName.isNotEmpty())) {
            return ProductInfo(
                name = label.wineName,
                brand = label.producerName,
                country = label.country,
                category = label.color,
                vintage = label.vintage?.toString(),
                region = label.regionName.takeIf { it.isNotEmpty() },
                source = displayName,
                externalId = candidateId,
                externalSource = if (candidateId != null) id else null,
            )
        }
        return topCandidate?.toProductInfo()
    }

    override suspend fun listProducers(): List<Producer> =
        client.listAllProducers(perPage = 100, maxPages = 10).map { p ->
            Producer(
                id = "gm-${p.id}",
                name = p.displayName.ifEmpty {
                    if (p.title.isNotEmpty()) "${p.title} ${p.name}" else p.name
                },
                country = "",
            )
        }

    override suspend fun listRegions(): List<Region> =
        client.listAllRegions(perPage = 100, maxPages = 25).map { r ->
            Region(id = "gm-${r.id}", name = r.name, country = r.country)
        }

    override suspend fun listWines(): List<Bottle> =
        client.listAllWines(perPage = 100, maxPages = 5).mapNotNull { it.toImportBottle() }

    override suspend fun enrich(externalId: String): WineEnrichment? {
        if (!GrapeMindsClient.isConfigured()) return null
        val wineId = externalId.filter { it.isDigit() }.toIntOrNull() ?: return null
        val wine = client.getWine(wineId)
        val drink = client.getDrinkingPeriod(wineId)
        if (wine == null && (drink == null || drink.generating)) return null
        val flavor = wine?.flavorProfile?.let { f ->
            FlavorProfile(
                sweetness = f.sweetness,
                acidity = f.acidity,
                tannins = f.tannins,
                alcohol = f.alcohol,
                body = f.body,
                finish = f.finish,
            )
        }
        return WineEnrichment(
            drinkFromYears = drink?.from?.takeIf { it >= 0 },
            drinkToYears = drink?.to?.takeIf { it >= 0 },
            maturity = drink?.statement ?: "",
            young = drink?.young ?: "",
            ripe = drink?.ripe ?: "",
            storage = drink?.storage ?: "",
            tastingNotes = wine?.tastingNotes?.text ?: "",
            description = wine?.description?.text ?: "",
            pairingText = wine?.pairing?.text ?: "",
            grapes = wine?.grapes?.map { it.name } ?: emptyList(),
            flavorProfile = flavor,
            source = displayName,
        )
    }

    private fun GmWineSummary.toProductInfo(): ProductInfo? {
        val brand = producer?.name?.takeIf { it.isNotEmpty() }
            ?: producerName.ifBlank { producerDisplayName }
        if (displayName.isEmpty() && brand.isEmpty()) return null
        val wineId = id.takeIf { it > 0 }?.toString()
        return ProductInfo(
            name = displayName,
            brand = brand,
            country = region?.country?.uppercase() ?: "",
            category = color,
            region = region?.name?.takeIf { it.isNotEmpty() },
            vintage = vintage?.toString(),
            source = this@GrapeMindsProvider.displayName,
            externalId = wineId,
            externalSource = if (wineId != null) "grapeminds" else null,
        )
    }

    private fun GmWineSummary.toImportBottle(): Bottle? {
        val domain = producer?.name?.takeIf { it.isNotEmpty() }
            ?: producer?.displayName?.takeIf { it.isNotEmpty() }
            ?: producerName.ifBlank { producerDisplayName }
        if (domain.isEmpty() && displayName.isEmpty()) return null
        return Bottle(
            id = "gm-$id",
            domain = domain,
            appellation = displayName,
            color = when {
                subType == "sparkling" -> WineColor.SPARKLING
                color == "white" -> WineColor.WHITE
                color == "rose" -> WineColor.ROSE
                else -> WineColor.RED
            },
            category = WineCategory.BORDEAUX,
            vintage = vintage?.toString() ?: "NM",
            price = 0,
            quantity = 1,
            rating = 0.0,
            cellarSpot = "—",
            provenance = region?.name ?: "",
            merchant = "—",
            purchaseDate = "",
            occasion = "",
        )
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
