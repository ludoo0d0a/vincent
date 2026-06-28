package fr.geoking.vincent.data

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
    // X-Wines / CellarTracker / the AI label reader.
    val vintage: String? = null,
    val grape: String? = null,
    val region: String? = null,
    val price: PriceInfo? = null,
    /** Display name of the provider that produced this result (UI/debug). */
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
