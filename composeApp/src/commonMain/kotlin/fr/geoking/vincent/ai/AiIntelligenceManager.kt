package fr.geoking.vincent.ai

import fr.geoking.vincent.data.ProductInfo
import fr.geoking.vincent.model.Bottle

/**
 * Result of querying the local cellar database via embedded LLM.
 */
data class DbQueryResult(
    val query: String,
    val matchedBottles: List<Bottle> = emptyList(),
    val explanation: String = "",
    val success: Boolean = true,
    val error: String? = null,
)

/**
 * Result of local OCR and sticker identification on a wine bottle.
 */
data class StickerIdentifyResult(
    val ocrText: String,
    val recognizedBottle: Bottle? = null,
    val localMatches: List<Bottle> = emptyList(),
    val catalogSuggestions: List<ProductInfo> = emptyList(),
    val confidence: Float = 0f,
    val error: String? = null,
)

/**
 * Manager for Android 18+ embedded LLM (Gemini Nano via AICore) and local AI operations.
 * Coordinates querying the local database using embedded AI and local bottle sticker identification.
 */
interface AiIntelligenceManager {
    /**
     * Checks whether local embedded LLM capabilities (e.g. Gemini Nano via AICore) are available.
     */
    suspend fun isEmbeddedLlmAvailable(): Boolean

    /**
     * Checks whether local OCR capability is available.
     */
    suspend fun isLocalOcrAvailable(): Boolean

    /**
     * Query local cellar database using embedded AI prompt interpretation.
     * Natural language query is translated to bottle filtering criteria against [allBottles].
     */
    suspend fun queryLocalDatabase(
        naturalLanguageQuery: String,
        allBottles: List<Bottle>,
    ): DbQueryResult

    /**
     * Identifies a bottle sticker / label using local OCR and matches against [allBottles] and catalog.
     */
    suspend fun identifyBottleSticker(
        jpegImage: ByteArray,
        allBottles: List<Bottle> = emptyList(),
    ): StickerIdentifyResult
}

// Expect function for platform provider.
expect fun getAiIntelligenceManager(): AiIntelligenceManager
