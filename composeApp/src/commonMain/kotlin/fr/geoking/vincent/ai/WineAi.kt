package fr.geoking.vincent.ai

import fr.geoking.vincent.model.Bottle

/** An estimated market price (always shown as an estimate, with its source/date). */
data class PriceEstimate(val amountEur: Int, val source: String, val asOf: String)

/**
 * Outcome of an AI recognition attempt — bottle and/or a user-visible error.
 * [reply] carries a short conversational confirmation when refining via discussion.
 */
data class RecognizeOutcome(val bottle: Bottle? = null, val error: String? = null, val reply: String? = null)

/** Resolves a noisy title or a label photo into a structured [Bottle]. */
interface WineRecognizer {
    suspend fun fromText(title: String): RecognizeOutcome
    suspend fun fromImage(jpeg: ByteArray): RecognizeOutcome

    /**
     * Refines an already-parsed [current] bottle from a free-form user instruction
     * (typed or dictated), e.g. "millésime 2019, 24 €". Returns the updated bottle
     * and an optional [RecognizeOutcome.reply] confirming what was completed.
     */
    suspend fun refine(current: Bottle, instruction: String): RecognizeOutcome
}

/** Estimates a bottle's current market price. */
interface PriceEstimator {
    suspend fun estimate(bottle: Bottle): PriceEstimate?
}

/** Result of a specific site price search. */
data class PriceSearchResult(val label: String, val price: Int, val url: String, val isFound: Boolean)

/** Searches for a bottle's price across multiple websites. */
interface PriceSearcher {
    fun search(bottle: Bottle): kotlinx.coroutines.flow.Flow<PriceSearchResult>
}

/** Suggests food pairings for a bottle. */
interface FoodPairer {
    suspend fun pairings(bottle: Bottle): List<String>
}

// Platform providers (Android = Gemini Flash). Other targets can return no-ops.
expect fun wineRecognizer(): WineRecognizer
expect fun priceEstimator(): PriceEstimator
expect fun priceSearcher(): PriceSearcher
expect fun foodPairer(): FoodPairer
