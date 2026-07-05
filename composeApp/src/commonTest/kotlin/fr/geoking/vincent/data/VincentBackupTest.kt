package fr.geoking.vincent.data

import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VincentBackupTest {

    @Test
    fun manifestRoundTrip() {
        val bottle = Bottle(
            id = "b1",
            domain = "Château Test",
            appellation = "Margaux",
            color = WineColor.RED,
            category = WineCategory.BORDEAUX,
            vintage = "2018",
            price = 42,
            quantity = 2,
            rating = 4.5,
            cellarSpot = "A1",
            provenance = "Bordeaux, FR",
            merchant = "Caviste",
            purchaseDate = "2024-01-01",
            occasion = "Dîner",
            source = AddSource.MANUAL,
            addedAt = 1_700_000_000_000,
        )
        val manifest = VincentManifestDto(
            exportedAt = 1_700_000_000_000,
            includesPhotos = false,
            bottles = listOf(bottle.toDtoForTest()),
        )
        val json = encodeVincentManifest(manifest)
        val decoded = decodeVincentManifest(json)
        assertEquals(1, decoded.bottles.size)
        assertEquals("Château Test", decoded.toDomain().bottles.first().domain)
    }

    @Test
    fun rejectsUnknownFormat() {
        val json = encodeVincentManifest(VincentManifestDto(format = "other"))
        assertFailsWith<IllegalStateException> {
            val manifest = decodeVincentManifest(json)
            if (manifest.format != VINCENT_BACKUP_FORMAT) error("unsupported format")
        }
    }

    @Test
    fun exportWithoutPhotosIsJsonNotZip() {
        val json = encodeVincentManifest(
            VincentManifestDto(
                includesPhotos = false,
                bottles = listOf(sampleBottleDto()),
            ),
        )
        val bytes = json.encodeToByteArray()
        assertTrue(bytes.first() == '{'.code.toByte())
    }

    @Test
    fun importBottlesMergeById() {
        val existing = Cellar.bottles.size
        val bottle = Bottle(
            id = "merge-test-bottle",
            domain = "Before",
            appellation = "App",
            color = WineColor.RED,
            category = WineCategory.BORDEAUX,
            vintage = "2020",
            price = 10,
            quantity = 1,
            rating = 4.0,
            cellarSpot = "A1",
            provenance = "FR",
            merchant = "Shop",
            purchaseDate = "2024-01-01",
            occasion = "",
            source = AddSource.MANUAL,
        )
        Cellar.importBottles(listOf(bottle))
        Cellar.importBottles(listOf(bottle.copy(domain = "After", quantity = 3)))
        val merged = Cellar.bottle("merge-test-bottle")
        assertEquals("After", merged?.domain)
        assertEquals(3, merged?.quantity)
        assertEquals(existing + 1, Cellar.bottles.size)
    }

    private fun sampleBottleDto() = BottleDto(
        id = "b1",
        domain = "Test",
        appellation = "App",
        color = WineColor.RED.name,
        category = WineCategory.BORDEAUX.name,
        vintage = "2020",
        price = 10,
        quantity = 1,
        rating = 4.0,
        cellarSpot = "A1",
        provenance = "FR",
        merchant = "Shop",
        purchaseDate = "2024-01-01",
        occasion = "",
    )

    private fun Bottle.toDtoForTest(): BottleDto = BottleDto(
        id = id,
        domain = domain,
        appellation = appellation,
        color = color.name,
        category = category.name,
        vintage = vintage,
        price = price,
        quantity = quantity,
        rating = rating,
        cellarSpot = cellarSpot,
        provenance = provenance,
        merchant = merchant,
        purchaseDate = purchaseDate,
        occasion = occasion,
        source = source.name,
        addedAt = addedAt,
    )
}
