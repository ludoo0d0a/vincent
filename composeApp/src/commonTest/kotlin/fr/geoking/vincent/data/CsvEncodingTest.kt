package fr.geoking.vincent.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvEncodingTest {
    @Test
    fun testEncoding() {
        val csv = "domaine,millésime\nDomaine de l'Étoile,2020"
        val result = CsvFormat.parse(csv)
        assertEquals(1, result.bottles.size)
        assertEquals("Domaine de l'Étoile", result.bottles[0].domain)
    }

    @Test
    fun testBOM() {
        val csv = "\uFEFFid,domain\n1,Test"
        val result = CsvFormat.parse(csv)
        assertEquals(1, result.bottles.size)
        assertEquals("Test", result.bottles[0].domain)
    }

    @Test
    fun testSemicolon() {
        val csv = "domaine;millésime\nDomaine de l'Étoile;2020"
        val result = CsvFormat.parse(csv)
        assertEquals(1, result.bottles.size)
        assertEquals("Domaine de l'Étoile", result.bottles[0].domain)
    }
}
