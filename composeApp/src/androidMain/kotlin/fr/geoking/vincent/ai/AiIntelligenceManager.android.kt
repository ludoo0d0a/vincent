package fr.geoking.vincent.ai

import android.util.Log
import fr.geoking.vincent.data.ProductInfo
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "AiIntelligenceManager"

class AndroidAiIntelligenceManager : AiIntelligenceManager {

    override suspend fun isEmbeddedLlmAvailable(): Boolean = withContext(Dispatchers.IO) {
        // Without managing model download flow, check if Gemma / Gemini Nano / AICore or on-device LLM is ready
        GemmaLlm.isAvailable || GemmaModel.isReady()
    }

    override suspend fun isLocalOcrAvailable(): Boolean {
        return true
    }

    override suspend fun queryLocalDatabase(
        naturalLanguageQuery: String,
        allBottles: List<Bottle>,
    ): DbQueryResult = withContext(Dispatchers.IO) {
        val query = naturalLanguageQuery.trim()
        if (query.isEmpty()) {
            return@withContext DbQueryResult(query = query, matchedBottles = allBottles)
        }

        // Use embedded LLM prompt to parse search intent into structured filters
        val prompt = "Extrait les critères de recherche pour une cave à vin sous forme de JSON " +
            "{color: string (rouge/blanc/rosé/pétillant), region: string, vintage: string, minPrice: int, maxPrice: int, keyword: string}. " +
            "Requête: \"$query\""

        var colorFilter: String? = null
        var regionFilter: String? = null
        var vintageFilter: String? = null
        var keywordFilter: String? = null

        if (isEmbeddedLlmAvailable()) {
            try {
                val json = GemmaLlm.generateJson(prompt)
                if (json != null) {
                    colorFilter = json.optString("color").takeIf { it.isNotBlank() && it != "null" }
                    regionFilter = json.optString("region").takeIf { it.isNotBlank() && it != "null" }
                    vintageFilter = json.optString("vintage").takeIf { it.isNotBlank() && it != "null" }
                    keywordFilter = json.optString("keyword").takeIf { it.isNotBlank() && it != "null" }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Embedded LLM DB query parsing failed: ${e.message}")
            }
        }

        // Fallback / local matching logic over allBottles
        val matched = allBottles.filter { bottle ->
            var matches = true
            if (colorFilter != null) {
                val expectedColor = when {
                    "rouge" in colorFilter.lowercase() -> WineColor.RED
                    "blanc" in colorFilter.lowercase() || "white" in colorFilter.lowercase() -> WineColor.WHITE
                    "ros" in colorFilter.lowercase() -> WineColor.ROSE
                    "pétill" in colorFilter.lowercase() || "petill" in colorFilter.lowercase() -> WineColor.SPARKLING
                    else -> null
                }
                if (expectedColor != null && bottle.color != expectedColor) {
                    matches = false
                }
            }
            if (matches && regionFilter != null) {
                val r = regionFilter.lowercase()
                val bottleProvenance = bottle.provenance.lowercase()
                val bottleAppellation = bottle.appellation.lowercase()
                if (!bottleProvenance.contains(r) && !bottleAppellation.contains(r)) {
                    matches = false
                }
            }
            if (matches && vintageFilter != null) {
                if (!bottle.vintage.contains(vintageFilter, ignoreCase = true)) {
                    matches = false
                }
            }
            if (matches && keywordFilter != null) {
                val kw = keywordFilter.lowercase()
                val searchTarget = "${bottle.domain} ${bottle.appellation} ${bottle.provenance} ${bottle.grapes.joinToString(" ")}".lowercase()
                if (!searchTarget.contains(kw)) {
                    matches = false
                }
            }
            // General query substring fallback if AI filters did not match or were null
            if (colorFilter == null && regionFilter == null && vintageFilter == null && keywordFilter == null) {
                val terms = query.lowercase().split(" ").filter { it.isNotBlank() }
                val target = "${bottle.domain} ${bottle.appellation} ${bottle.provenance} ${bottle.vintage} ${bottle.color} ${bottle.grapes.joinToString(" ")}".lowercase()
                matches = terms.all { target.contains(it) }
            }
            matches
        }

        DbQueryResult(
            query = query,
            matchedBottles = matched,
            explanation = "Trouvé ${matched.size} bouteille(s) correspondant à la recherche.",
            success = true,
        )
    }

    override suspend fun identifyBottleSticker(
        jpegImage: ByteArray,
        allBottles: List<Bottle>,
    ): StickerIdentifyResult = withContext(Dispatchers.IO) {
        if (jpegImage.isEmpty()) {
            return@withContext StickerIdentifyResult(
                ocrText = "",
                error = "Image vide",
            )
        }

        val ocrText = try {
            labelOcr().recognize(downscaleJpeg(jpegImage))
        } catch (e: Exception) {
            Log.w(TAG, "Local OCR failed: ${e.message}")
            ""
        }

        val outcome = WineAiEngine.fromImage(jpegImage)

        // Find matches in local cellar database
        val localMatches = if (ocrText.isNotBlank()) {
            val terms = ocrText.lowercase().split(Regex("\\s+")).filter { it.length > 3 }
            allBottles.filter { bottle ->
                val target = "${bottle.domain} ${bottle.appellation} ${bottle.provenance}".lowercase()
                terms.any { target.contains(it) }
            }
        } else {
            emptyList()
        }

        StickerIdentifyResult(
            ocrText = ocrText,
            recognizedBottle = outcome.bottle,
            localMatches = localMatches,
            catalogSuggestions = outcome.suggestions,
            confidence = if (outcome.bottle != null) 0.85f else if (ocrText.isNotBlank()) 0.5f else 0.0f,
            error = outcome.error,
        )
    }
}

actual fun getAiIntelligenceManager(): AiIntelligenceManager = AndroidAiIntelligenceManager()
