package fr.geoking.vincent.ai

import fr.geoking.vincent.model.Bottle

/** An estimated market price (always shown as an estimate, with its source/date). */
data class PriceEstimate(val amountEur: Int, val source: String, val asOf: String)

/** Resolves a noisy title or a label photo into a structured [Bottle]. */
interface WineRecognizer {
    suspend fun fromText(title: String): Bottle?
    suspend fun fromImage(jpeg: ByteArray): Bottle?
}

/** Estimates a bottle's current market price. */
interface PriceEstimator {
    suspend fun estimate(bottle: Bottle): PriceEstimate?
}

/** Suggests food pairings for a bottle. */
interface FoodPairer {
    suspend fun pairings(bottle: Bottle): List<String>
}

// Platform providers (Android = Gemini Flash). Other targets can return no-ops.
expect fun wineRecognizer(): WineRecognizer
expect fun priceEstimator(): PriceEstimator
expect fun foodPairer(): FoodPairer
