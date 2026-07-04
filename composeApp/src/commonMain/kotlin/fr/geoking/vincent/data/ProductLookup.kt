package fr.geoking.vincent.data

import fr.geoking.vincent.model.FlavorProfile
import fr.geoking.vincent.model.Producer
import fr.geoking.vincent.model.Region
import fr.geoking.vincent.model.Bottle

/** A capability a [WineDataProvider] may expose. */
enum class ProviderCapability {
    /** Resolve an EAN/UPC barcode into a [ProductInfo]. */
    BARCODE_SCAN,

    /** Resolve a label photo (jpeg bytes) into a [ProductInfo]. */
    LABEL_SCAN,

    /** Free-text search (name, domain, grape, region…) returning candidates. */
    TEXT_SEARCH,

    /** Estimate/lookup a market price for a wine query. */
    PRICE,

    /** Fetch rich detail (drink window, tasting notes, pairings…) for a known [ProductInfo.externalId]. */
    ENRICH,

    /** List producers from the provider. */
    LIST_PRODUCERS,

    /** List regions from the provider. */
    LIST_REGIONS,

    /** List wines from the provider. */
    LIST_WINES,
}

/** An estimated/looked-up price attached to a product. */
data class PriceInfo(
    val amount: Double,
    val currency: String,
    val source: String? = null,
)

/** Product facts resolved from a barcode, a label photo, or a text query. */
data class ProductInfo(
    val name: String,
    val brand: String,
    val country: String,
    val category: String,
    /** Front label photo (e.g. from Open Food Facts), when one is available. */
    val imageUrl: String? = null,
    // Wine-specific fields, often missing from Open Food Facts but provided by
    // CellarTracker / the AI label reader.
    val vintage: String? = null,
    val grape: String? = null,
    val region: String? = null,
    val price: PriceInfo? = null,
    /** Display name of the provider that produced this result (UI/debug). */
    val source: String? = null,
    /** Stable id of this wine in the provider's catalogue, for a follow-up [WineDataSource.enrich] call. */
    val externalId: String? = null,
    /** Id of the provider that owns [externalId] (matches [WineDataProvider.id]). */
    val externalSource: String? = null,
)

/**
 * Rich, on-demand detail for a wine already identified by [ProductInfo.externalId] — fetched
 * lazily (e.g. when a bottle is added) rather than during search. All fields are optional; a
 * provider fills what it has. [drinkFromYears]/[drinkToYears] are either offsets in years
 * from the vintage, or absolute calendar years when the value is ≥ 1900 (grapeminds API).
 */
data class WineEnrichment(
    val drinkFromYears: Int? = null,
    val drinkToYears: Int? = null,
    val maturity: String = "",       // one-line drink-window statement
    val young: String = "",          // how it tastes young
    val ripe: String = "",           // how it tastes mature
    val storage: String = "",        // storage recommendation
    val tastingNotes: String = "",
    val description: String = "",
    val pairingText: String = "",    // prose food-pairing note
    val grapes: List<String> = emptyList(),
    val flavorProfile: FlavorProfile? = null,
    val source: String? = null,
)

/**
 * A pluggable wine data source. Each provider declares its [capabilities];
 * unsupported operations keep their default (null/empty) result so callers can
 * route blindly without crashing.
 */
interface WineDataProvider {
    val id: String
    val displayName: String
    val capabilities: Set<ProviderCapability>

    suspend fun byBarcode(code: String): ProductInfo? = null
    suspend fun byLabel(imageBytes: ByteArray): ProductInfo? = null
    suspend fun search(query: String): List<ProductInfo> = emptyList()
    suspend fun price(query: String): PriceInfo? = null
    suspend fun enrich(externalId: String): WineEnrichment? = null
    suspend fun listProducers(): List<Producer> = emptyList()
    suspend fun listRegions(): List<Region> = emptyList()
    suspend fun listWines(): List<Bottle> = emptyList()
}

/** Platform-provided list of available providers (order = priority). */
expect fun wineDataProviders(): List<WineDataProvider>

/**
 * Routes each operation to the providers that declare the matching capability,
 * trying them in priority order and returning the first usable result.
 */
object WineDataSource {

    private val providers: List<WineDataProvider> by lazy { wineDataProviders() }

    fun providers(): List<WineDataProvider> = providers

    fun supporting(capability: ProviderCapability): List<WineDataProvider> =
        providers.filter { capability in it.capabilities }

    suspend fun byBarcode(code: String): ProductInfo? =
        supporting(ProviderCapability.BARCODE_SCAN)
            .firstNotNullOfOrNull { it.byBarcode(code) }

    suspend fun byLabel(imageBytes: ByteArray): ProductInfo? =
        supporting(ProviderCapability.LABEL_SCAN)
            .firstNotNullOfOrNull { it.byLabel(imageBytes) }

    suspend fun search(query: String): List<ProductInfo> =
        supporting(ProviderCapability.TEXT_SEARCH)
            .flatMap { it.search(query) }

    suspend fun price(query: String): PriceInfo? =
        supporting(ProviderCapability.PRICE)
            .firstNotNullOfOrNull { it.price(query) }

    /**
     * Fetch rich detail for a wine previously surfaced by a provider. Routes to the provider
     * named by [source] (a [ProductInfo.externalSource]) when it supports enrichment, else to
     * the first enrich-capable provider. Returns null when none can resolve [externalId].
     */
    suspend fun enrich(source: String?, externalId: String): WineEnrichment? {
        val candidates = supporting(ProviderCapability.ENRICH)
        val provider = candidates.firstOrNull { it.id == source } ?: candidates.firstOrNull()
        return provider?.enrich(externalId)
    }

    suspend fun listProducers(source: String? = null): List<Producer> {
        val candidates = supporting(ProviderCapability.LIST_PRODUCERS)
        val provider = candidates.firstOrNull { it.id == source } ?: candidates.firstOrNull()
        return provider?.listProducers() ?: emptyList()
    }

    suspend fun listRegions(source: String? = null): List<Region> {
        val candidates = supporting(ProviderCapability.LIST_REGIONS)
        val provider = candidates.firstOrNull { it.id == source } ?: candidates.firstOrNull()
        return provider?.listRegions() ?: emptyList()
    }

    suspend fun listWines(source: String? = null): List<Bottle> {
        val candidates = supporting(ProviderCapability.LIST_WINES)
        val provider = candidates.firstOrNull { it.id == source } ?: candidates.firstOrNull()
        return provider?.listWines() ?: emptyList()
    }
}

/**
 * Backwards-compatible seam kept for existing callers (e.g. AddScreen). Delegates
 * to [WineDataSource.byBarcode], which fans out across all barcode providers.
 */
interface BarcodeLookup {
    suspend fun byBarcode(code: String): ProductInfo?
}

fun barcodeLookup(): BarcodeLookup = object : BarcodeLookup {
    override suspend fun byBarcode(code: String): ProductInfo? = WineDataSource.byBarcode(code)
}
