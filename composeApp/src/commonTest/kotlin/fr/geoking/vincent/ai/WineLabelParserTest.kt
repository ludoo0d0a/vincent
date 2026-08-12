package fr.geoking.vincent.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WineLabelParserTest {

    @Test
    fun parsesClassicFrenchLabel() {
        val text = """
            Chateau Margaux
            Grand Cru Classe
            Margaux
            2015
            13,5% vol.
        """.trimIndent()
        val p = WineLabelParser.parse(text)
        assertTrue(p.domain.contains("Margaux", ignoreCase = true) || p.domain.contains("Chateau", ignoreCase = true))
        assertEquals("2015", p.vintage)
        assertEquals(13.5, p.alcohol)
        assertTrue(p.appellation.contains("Margaux", ignoreCase = true) || p.region.contains("Margaux"))
        assertTrue(p.isConfident)
    }

    @Test
    fun parsesSpokenTitle() {
        val p = WineLabelParser.parse("Domaine Tempier Bandol rouge 2018")
        assertTrue(p.domain.contains("Tempier", ignoreCase = true) || p.domain.startsWith("Domaine"))
        assertEquals("2018", p.vintage)
        assertEquals("rouge", p.colorHint)
        assertTrue(p.isConfident)
    }

    @Test
    fun weakWithoutDomainSignals() {
        val p = WineLabelParser.parse("hello world")
        assertFalse(p.isConfident)
    }

    @Test
    fun emptyIsNotConfident() {
        assertFalse(WineLabelParser.parse("").isConfident)
        assertFalse(WineLabelParser.parse("   ").isConfident)
    }

    @Test
    fun detectsColorAndGrapes() {
        val p = WineLabelParser.parse(
            """
            Maison Louis Latour
            Meursault
            Chardonnay
            blanc
            2020
            """.trimIndent(),
        )
        assertEquals("blanc", p.colorHint)
        assertTrue(p.grapes.any { it.contains("Chardonnay", ignoreCase = true) })
        assertEquals("2020", p.vintage)
        assertTrue(p.isConfident)
    }

    @Test
    fun searchQueryJoinsFields() {
        val p = WineLabelParser.parse("Chateau Palmer Margaux 2016")
        assertTrue(p.searchQuery.contains("Palmer") || p.searchQuery.contains("Chateau"))
        assertTrue(p.searchQuery.contains("2016"))
    }
}
