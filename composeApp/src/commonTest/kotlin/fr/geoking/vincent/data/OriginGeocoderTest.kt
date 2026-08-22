package fr.geoking.vincent.data

import fr.geoking.vincent.model.Appellation
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OriginGeocoderTest {

    @BeforeTest
    fun setup() {
        OriginGeocoder.loadCentroids(
            """
            {
              "macroRegions": {
                "bordeaux": {"lat": 44.84, "lon": -0.58, "label": "Bordeaux"},
                "rioja": {"lat": 42.5, "lon": -2.5, "label": "Rioja"}
              },
              "countries": {
                "ES": {"lat": 40.0, "lon": -3.7, "label": "Espagne"}
              },
              "aliases": {
                "rioja": "rioja",
                "espagne": "ES"
              }
            }
            """.trimIndent(),
        )
        Appellations.import(
            listOf(
                Appellation(id = "inao-42", name = "Margaux", sign = "AOC", department = "33", geoAsset = "42.geojson"),
            ),
        )
    }

    @Test
    fun resolvesAppellationFromName() {
        val bottle = sampleBottle(appellation = "Margaux", provenance = "Saint-Julien, FR")
        val origin = OriginGeocoder.resolveOrigin(bottle)
        assertEquals(OriginKind.Appellation, origin.kind)
        assertEquals("app:inao-42", origin.key)
        assertEquals("42.geojson", origin.geoAsset)
    }

    @Test
    fun resolvesForeignRegionFromProvenance() {
        val bottle = sampleBottle(
            appellation = "",
            provenance = "Rioja",
            category = WineCategory.BORDEAUX,
        )
        val origin = OriginGeocoder.resolveOrigin(bottle)
        assertEquals(OriginKind.MacroRegion, origin.kind)
        assertEquals("macro:rioja", origin.key)
        assertNotNull(origin.latLon)
    }

    @Test
    fun resolvesCountryFromTrailingCode() {
        val bottle = sampleBottle(
            appellation = "",
            provenance = "Unknown winery, ES",
            category = WineCategory.BORDEAUX,
        )
        val origin = OriginGeocoder.resolveOrigin(bottle)
        assertEquals(OriginKind.Country, origin.kind)
        assertEquals("country:ES", origin.key)
    }

    @Test
    fun fallsBackToUnmappedWhenEmpty() {
        val bottle = sampleBottle(appellation = "", provenance = "", domain = "Mystery Wine")
        val origin = OriginGeocoder.resolveOrigin(bottle)
        assertEquals(OriginKind.Unmapped, origin.kind)
        assertTrue(origin.label.contains("Mystery"))
    }

    @Test
    fun aggregatesByOriginKey() {
        Cellar.bottles.clear()
        Cellar.bottles.add(sampleBottle(appellation = "Margaux", provenance = "Margaux, FR", id = "b1"))
        Cellar.bottles.add(sampleBottle(appellation = "Margaux", provenance = "Margaux, FR", id = "b2", quantity = 2))
        val aggregates = CellarOrigins.aggregate()
        assertEquals(1, aggregates.size)
        assertEquals(3, aggregates.first().bottleCount)
    }

    private fun sampleBottle(
        id: String = "test",
        appellation: String,
        provenance: String,
        domain: String = "Domain",
        category: WineCategory = WineCategory.BORDEAUX,
        quantity: Int = 1,
    ) = Bottle(
        id = id,
        domain = domain,
        appellation = appellation,
        color = WineColor.RED,
        category = category,
        vintage = "2020",
        price = 20,
        quantity = quantity,
        rating = 4.0,
        cellarSpot = "A1",
        provenance = provenance,
        merchant = "Test",
        purchaseDate = "2024",
        occasion = "Test",
    )
}
