package fr.geoking.vincent.data

/** Minimal product facts resolved from a barcode (EAN/UPC). */
data class ProductInfo(
    val name: String,
    val brand: String,
    val country: String,
    val category: String,
    /** Front label photo from Open Food Facts, when contributors uploaded one. */
    val imageUrl: String? = null,
)

/**
 * Resolves a barcode (EAN/UPC) into [ProductInfo]. The Android actual queries
 * Open Food Facts (free, no API key). Returns `null` when the code is unknown —
 * the caller falls back to label photo (AI) or manual entry.
 */
interface BarcodeLookup {
    suspend fun byBarcode(code: String): ProductInfo?
}

expect fun barcodeLookup(): BarcodeLookup
