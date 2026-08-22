package fr.geoking.vincent.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikidataSparqlTest {

    @Test
    fun languageCodeFallsBackToFr() {
        assertEquals("fr", WikidataSparql.languageCode("fr-FR"))
        assertEquals("en", WikidataSparql.languageCode("en"))
        assertEquals("fr", WikidataSparql.languageCode("zh-CN"))
    }

    @Test
    fun qidFromUri() {
        assertEquals("Q531", WikidataSparql.qidFromUri("http://www.wikidata.org/entity/Q531"))
        assertNull(WikidataSparql.qidFromUri("http://www.wikidata.org/entity/P18"))
    }

    @Test
    fun commonsImageUrlUpgradesHttpAndFilenames() {
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/Chateau%20Margaux.jpg",
            WikidataSparql.commonsImageUrl(
                "http://commons.wikimedia.org/wiki/Special:FilePath/Chateau%20Margaux.jpg",
            ),
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/Foo_Bar.jpg",
            WikidataSparql.commonsImageUrl("File:Foo Bar.jpg"),
        )
        assertNull(WikidataSparql.commonsImageUrl(""))
    }

    @Test
    fun escapeLiteralEscapesQuotes() {
        assertEquals("""Château \"Margaux\"""", WikidataSparql.escapeLiteral("""Château "Margaux""""))
    }

    @Test
    fun searchQueryContainsEntitySearchAndLimit() {
        val q = WikidataSparql.searchQuery("Margaux", "fr", limit = 10)
        assertTrue("EntitySearch" in q)
        assertTrue("Margaux" in q)
        assertTrue("LIMIT 10" in q)
        assertTrue("wd:Q2085381" in q)
    }

    @Test
    fun parseSearchResultsDedupesAndSkipsQidLabels() {
        val results = WikidataSparql.parseSearchResults(fixture("wikidata-search-margaux.json"))
        assertEquals(1, results.size)
        val hit = results.first()
        assertEquals("Château Margaux", hit.name)
        assertEquals("Q531", hit.externalId)
        assertEquals(WikidataSparql.PROVIDER_ID, hit.externalSource)
        assertEquals(WikidataSparql.DISPLAY_NAME, hit.source)
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/Chateau%20Margaux.jpg",
            hit.imageUrl,
        )
        assertEquals("domaine viticole à Margaux", hit.region)
    }

    @Test
    fun parseEnrichmentCollectsGrapes() {
        val enr = WikidataSparql.parseEnrichment(fixture("wikidata-enrich-q531.json"))
        assertNotNull(enr)
        assertEquals("domaine viticole à Margaux", enr.description)
        assertEquals("France", enr.country)
        assertEquals("Margaux", enr.regionName)
        assertEquals(listOf("Cabernet sauvignon", "Merlot"), enr.grapes)
        assertEquals(WikidataSparql.DISPLAY_NAME, enr.source)
    }

    @Test
    fun parseRegionsMapsFrance() {
        val regions = WikidataSparql.parseRegions(fixture("wikidata-regions-fr.json"))
        assertEquals(2, regions.size)
        assertEquals("wd-Q202386", regions[0].id)
        assertEquals("Bordeaux", regions[0].name)
        assertEquals("France", regions[0].country)
        assertEquals("région viticole", regions[0].description)
        assertEquals("Bourgogne", regions[1].name)
    }

    @Test
    fun parseEmptyJsonReturnsEmpty() {
        assertEquals(emptyList(), WikidataSparql.parseSearchResults("{}"))
        assertNull(WikidataSparql.parseEnrichment("{}"))
        assertEquals(emptyList(), WikidataSparql.parseRegions("not-json"))
    }

    private fun fixture(name: String): String {
        val candidates = listOf(
            File("src/commonTest/resources/$name"),
            File("composeApp/src/commonTest/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Missing fixture: ${candidates.joinToString { it.path }}")
        return file.readText()
    }
}
