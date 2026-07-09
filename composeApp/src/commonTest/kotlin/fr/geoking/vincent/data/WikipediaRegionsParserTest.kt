package fr.geoking.vincent.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WikipediaRegionsParserTest {
    private val expectedFrenchRegions = listOf(
        "Alsace",
        "Beaujolais",
        "Bordeaux",
        "Bourgogne",
        "Bretagne",
        "Bugey",
        "Champagne",
        "Charentes",
        "Corse",
        "Jura",
        "La Réunion",
        "Languedoc-Roussillon",
        "Lorraine",
        "Lyonnais",
        "Nord-Pas-de-Calais",
        "Normandie",
        "Picardie",
        "Provence",
        "Savoie",
        "Sud-Ouest",
        "Tahiti",
        "vallée de la Loire",
        "vallée du Rhône",
        "Île-de-France",
    )

    private val expectedEnglishRegions = listOf(
        "Alsace",
        "Beaujolais",
        "Bordeaux",
        "Burgundy",
        "Champagne",
        "Corsica",
        "Jura",
        "Languedoc-Roussillon",
        "Loire Valley",
        "Provence",
        "Rhône",
        "Savoy",
        "South West France",
    )

    @Test
    fun urlUsesFrenchArticleByDefault() {
        assertEquals(
            "https://fr.wikipedia.org/wiki/Viticulture_en_France",
            WikipediaRegionsParser.url("fr"),
        )
    }

    @Test
    fun urlUsesEnglishArticleForEnLocale() {
        assertEquals(
            "https://en.wikipedia.org/wiki/French_wine",
            WikipediaRegionsParser.url("en"),
        )
    }

    @Test
    fun parseExtractsFrenchRegionsFromParsoidNavboxHtml() {
        assertEquals(
            expectedFrenchRegions,
            WikipediaRegionsParser.parse(fixtureHtml("wikipedia-french-regions-navbox.html"), "fr"),
        )
    }

    @Test
    fun parseExtractsEnglishRegionsFromParsoidNavboxHtml() {
        assertEquals(
            expectedEnglishRegions,
            WikipediaRegionsParser.parse(fixtureHtml("wikipedia-english-regions-navbox.html"), "en"),
        )
    }

    @Test
    fun parseStillHandlesLegacyWikiLinkFormat() {
        val legacyHtml = """
            <table>Régions viticoles françaises
              <tr><th>Produisant du vin d'<a href="/wiki/AOC">AOC</a></th>
              <td><a href="/wiki/Vignoble_d'Alsace" title="Vignoble d'Alsace">Alsace</a></td></tr>
              <tr><th>Ne produisant pas de vin d'AOC</th>
              <td><a href="/wiki/Vignoble_de_Bretagne" title="Vignoble de Bretagne">Bretagne</a></td></tr>
            </table>
        """.trimIndent()
        assertEquals(listOf("Alsace", "Bretagne"), WikipediaRegionsParser.parse(legacyHtml, "fr"))
    }

    @Test
    fun parseFallsBackToFrenchForUnsupportedLanguage() {
        assertEquals(
            expectedFrenchRegions,
            WikipediaRegionsParser.parse(fixtureHtml("wikipedia-french-regions-navbox.html"), "de"),
        )
    }

    @Test
    fun parseReturnsEmptyListWhenPaletteIsMissing() {
        assertEquals(emptyList(), WikipediaRegionsParser.parse("<html><body>no regions here</body></html>", "fr"))
    }

    private fun fixtureHtml(name: String): String {
        val candidates = listOf(
            File("src/commonTest/resources/$name"),
            File("composeApp/src/commonTest/resources/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Missing fixture: ${candidates.joinToString { it.path }}")
        return file.readText()
    }
}
