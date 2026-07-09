package fr.geoking.vincent.data

/** Parses French wine region names from localized Wikipedia article HTML. */
object WikipediaRegionsParser {
    private const val DEFAULT_LANGUAGE = "fr"

    private val wikiLinkRe = Regex(
        """<a[^>]+href="(?:https?:)?(?://[a-z]{2,3}\.wikipedia\.org)?/wiki/[^"]*"[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val frenchSkipNames = Regex(
        """^(AOC|IGP|Vin|Eaux-de-vie|v · m|Modifier|Portail|Liste)$""",
        RegexOption.IGNORE_CASE,
    )
    private val englishSkipNames = Regex(
        """^(French wine|AOC|PDO|IGP|v|t|e|Portail|Liste)$""",
        RegexOption.IGNORE_CASE,
    )

    fun supportedLanguage(language: String): String =
        when (language.lowercase().substringBefore('-')) {
            "en" -> "en"
            "fr" -> "fr"
            else -> DEFAULT_LANGUAGE
        }

    fun url(language: String = DEFAULT_LANGUAGE): String =
        when (supportedLanguage(language)) {
            "en" -> "https://en.wikipedia.org/wiki/French_wine"
            else -> "https://fr.wikipedia.org/wiki/Viticulture_en_France"
        }

    fun parse(html: String, language: String = DEFAULT_LANGUAGE): List<String> =
        when (supportedLanguage(language)) {
            "en" -> parseEnglish(html)
            else -> parseFrench(html)
        }

    private fun parseFrench(html: String): List<String> {
        val regions = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        val paletteMatch = Regex("""Régions viticoles françaises[\s\S]*?</table>""").find(html)
        if (paletteMatch != null) {
            val palette = paletteMatch.value
            Regex("""Produisant du vin d'[\s\S]*?(?=</tr>)""").find(palette)?.let {
                collectLinks(it.value, regions, seen, frenchSkipNames)
            }
            Regex("""Ne produisant pas de vin d'AOC[\s\S]*?(?=</tr>)""").find(palette)?.let {
                collectLinks(it.value, regions, seen, frenchSkipNames)
            }
        }

        if (regions.isEmpty()) {
            val listMatch = Regex("""id="Principaux_vignobles"[\s\S]*?</table>""").find(html)
            if (listMatch != null) {
                collectTableRows(listMatch.value, regions, seen, frenchSkipNames)
            }
        }

        return regions.sorted()
    }

    private fun parseEnglish(html: String): List<String> {
        val regions = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        val paletteMatch = Regex(
            """French wine</a></div></th></tr>[\s\S]*?</table>""",
            RegexOption.IGNORE_CASE,
        ).find(html)
        if (paletteMatch != null) {
            collectLinks(paletteMatch.value, regions, seen, englishSkipNames)
        }

        if (regions.isEmpty()) {
            val listMatch = Regex("""id="Wine_regions_of_France"[\s\S]*?</table>""").find(html)
            if (listMatch != null) {
                collectTableRows(listMatch.value, regions, seen, englishSkipNames)
            }
        }

        return regions.sorted()
    }

    private fun collectTableRows(
        section: String,
        regions: MutableList<String>,
        seen: MutableSet<String>,
        skipNames: Regex,
    ) {
        val rowRe = Regex(
            """<tr[^>]*>[\s\S]*?<td[^>]*>[\s\S]*?<a[^>]+href="[^"]*/wiki/[^"]*"[^>]*>([^<]+)</a>""",
        )
        for (row in rowRe.findAll(section)) {
            val name = cleanRegionName(row.groupValues[1])
            if (name.length > 2 && name !in seen && !skipNames.matches(name)) {
                regions += name
                seen += name
            }
        }
    }

    private fun collectLinks(
        section: String,
        regions: MutableList<String>,
        seen: MutableSet<String>,
        skipNames: Regex,
    ) {
        for (link in wikiLinkRe.findAll(section)) {
            val name = cleanRegionName(link.groupValues[1])
            if (name.isNotEmpty() && '<' !in name && name !in seen && !skipNames.matches(name)) {
                regions += name
                seen += name
            }
        }
    }

    private fun cleanRegionName(raw: String): String =
        decodeHtmlEntities(raw)
            .replace(Regex("""Vignoble d[e']""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""Vignoble du""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^vignoble d[e']""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^vignoble de""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^vignoble du""", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun decodeHtmlEntities(text: String): String =
        text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
}
