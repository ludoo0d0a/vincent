package fr.geoking.vincent.data

import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VincentArchiveTest {

    @Test
    fun packUnpackRoundTripWithPhotos() {
        val manifest = encodeVincentManifest(
            VincentManifestDto(
                includesPhotos = true,
                bottles = listOf(
                    BottleDto(
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
                        photoLabel = "photos/bottles/b1-label.jpg",
                    ),
                ),
            ),
        )
        val photoBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val archive = VincentArchive.pack(manifest, mapOf("photos/bottles/b1-label.jpg" to photoBytes))
        assertTrue(archive[0] == 0x50.toByte() && archive[1] == 0x4B.toByte())

        val payload = VincentArchive.unpack(archive)
        assertEquals(manifest, payload.manifestJson)
        assertEquals(photoBytes.toList(), payload.photos["photos/bottles/b1-label.jpg"]!!.toList())

        val parsed = VincentBackup.parseImport(archive)
        assertEquals("Test", parsed.manifest.bottles.first().domain)
        assertEquals(1, parsed.photos.size)
    }

    @Test
    fun rejectsZipWithoutManifest() {
        val zipWithoutManifest = java.io.ByteArrayOutputStream().use { out ->
            java.util.zip.ZipOutputStream(out).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("photos/bottles/x.jpg"))
                z.write(byteArrayOf(1, 2, 3))
                z.closeEntry()
            }
            out.toByteArray()
        }
        assertFailsWith<IllegalStateException> {
            VincentArchive.unpack(zipWithoutManifest)
        }
    }
}
