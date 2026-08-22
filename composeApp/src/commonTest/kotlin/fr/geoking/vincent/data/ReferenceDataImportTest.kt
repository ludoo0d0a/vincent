package fr.geoking.vincent.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceDataImportTest {

    private val sampleGrapes = """
        {"grapes":[
          {"name":"Merlot","vivcNumber":191,"color":"red","country":"FR"},
          {"id":"vivc-5","name":"Cabernet Sauvignon","color":"red","vivcNumber":5}
        ]}
    """.trimIndent()

    private val sampleAppellations = """
        [{"name":"Margaux","sign":"AOC","inaoId":42,"department":"33","geoAsset":"42.geojson"}]
    """.trimIndent()

    @Test
    fun parseGrapesJsonEnvelope() {
        val grapes = ReferenceDataImport.parseGrapesJson(sampleGrapes)
        assertEquals(2, grapes.size)
        assertEquals("vivc-191", grapes[0].id)
        assertEquals("Merlot", grapes[0].name)
        assertEquals("red", grapes[0].color)
    }

    @Test
    fun parseAppellationsJsonArray() {
        val apps = ReferenceDataImport.parseAppellationsJson(sampleAppellations)
        assertEquals(1, apps.size)
        assertEquals("inao-42", apps[0].id)
        assertEquals("Margaux", apps[0].name)
        assertEquals("42.geojson", apps[0].geoAsset)
    }

    @Test
    fun grapesSearchUsesImportedNames() {
        val imported = ReferenceDataImport.parseGrapesJson(sampleGrapes)
        Grapes.import(imported)
        val hits = Grapes.search("mer", limit = 5)
        assertTrue(hits.any { it.name == "Merlot" })
    }
}
