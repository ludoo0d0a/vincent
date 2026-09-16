package fr.geoking.vincent.ai

import fr.geoking.vincent.data.ProductInfo
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeAiIntelligenceManager : AiIntelligenceManager {
    override suspend fun isEmbeddedLlmAvailable(): Boolean = true
    override suspend fun isLocalOcrAvailable(): Boolean = true

    override suspend fun queryLocalDatabase(
        naturalLanguageQuery: String,
        allBottles: List<Bottle>,
    ): DbQueryResult {
        val q = naturalLanguageQuery.lowercase()
        val matched = allBottles.filter { bottle ->
            if ("rouge" in q && bottle.color != WineColor.RED) return@filter false
            if ("bordeaux" in q && !bottle.provenance.lowercase().contains("bordeaux")) return@filter false
            true
        }
        return DbQueryResult(
            query = naturalLanguageQuery,
            matchedBottles = matched,
            explanation = "Matched ${matched.size} bottles",
            success = true,
        )
    }

    override suspend fun identifyBottleSticker(
        jpegImage: ByteArray,
        allBottles: List<Bottle>,
    ): StickerIdentifyResult {
        if (jpegImage.isEmpty()) {
            return StickerIdentifyResult(ocrText = "", error = "Empty image")
        }
        val dummyText = "Château Margaux 2015 Bordeaux"
        val matched = allBottles.filter { bottle ->
            bottle.domain.contains("Margaux", ignoreCase = true)
        }
        return StickerIdentifyResult(
            ocrText = dummyText,
            recognizedBottle = matched.firstOrNull(),
            localMatches = matched,
            catalogSuggestions = listOf(ProductInfo(name = "Margaux 2015", brand = "Château Margaux", country = "France", category = "Vin")),
            confidence = 0.9f,
        )
    }
}

class AiIntelligenceManagerTest {

    private val sampleBottles = listOf(
        Bottle(
            id = "1",
            domain = "Château Margaux",
            appellation = "Margaux",
            color = WineColor.RED,
            category = WineCategory.BORDEAUX,
            vintage = "2015",
            price = 500,
            quantity = 1,
            rating = 4.8,
            cellarSpot = "A1",
            provenance = "Bordeaux",
            merchant = "Maison",
            purchaseDate = "2020-01-01",
            occasion = "Garder",
            source = AddSource.MANUAL,
        ),
        Bottle(
            id = "2",
            domain = "Domaine Leflaive",
            appellation = "Puligny-Montrachet",
            color = WineColor.WHITE,
            category = WineCategory.BOURGOGNE,
            vintage = "2018",
            price = 150,
            quantity = 2,
            rating = 4.5,
            cellarSpot = "B2",
            provenance = "Bourgogne",
            merchant = "Cave",
            purchaseDate = "2021-05-01",
            occasion = "Grande occasion",
            source = AddSource.MANUAL,
        ),
    )

    @Test
    fun testQueryLocalDatabase() = runBlocking {
        val manager: AiIntelligenceManager = FakeAiIntelligenceManager()
        assertTrue(manager.isEmbeddedLlmAvailable())

        val result = manager.queryLocalDatabase("vins rouges de bordeaux", sampleBottles)
        assertEquals(true, result.success)
        assertEquals(1, result.matchedBottles.size)
        assertEquals("Château Margaux", result.matchedBottles.first().domain)
    }

    @Test
    fun testIdentifyBottleSticker() = runBlocking {
        val manager: AiIntelligenceManager = FakeAiIntelligenceManager()
        val dummyBytes = byteArrayOf(1, 2, 3, 4)

        val result = manager.identifyBottleSticker(dummyBytes, sampleBottles)
        assertNotNull(result.ocrText)
        assertEquals(1, result.localMatches.size)
        assertEquals("Château Margaux", result.localMatches.first().domain)
        assertEquals(1, result.catalogSuggestions.size)
    }
}
