package fr.geoking.vincent.data

import fr.geoking.vincent.model.Region
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * SPARQL query builders + JSON parsers for Wikidata ([query.wikidata.org]).
 * HTTP lives in [WikidataClient] (androidMain); this object is unit-tested offline.
 */
object WikidataSparql {
    const val ENDPOINT = "https://query.wikidata.org/sparql"
    const val USER_AGENT = "Vincent/1.0 (https://vincent.geoking.fr; Android wine cellar)"
    const val DISPLAY_NAME = "Wikidata"
    const val PROVIDER_ID = "wikidata"
    const val SEARCH_LIMIT = 10
    const val REGIONS_LIMIT = 200

    /** Winery, wine region, AOC, wine appellation, wine. */
    private val wineRelatedClasses = listOf(
        "wd:Q2085381", // winery
        "wd:Q2335854", // wine-producing region
        "wd:Q2978441", // Appellation d'origine contrôlée
        "wd:Q4162218", // wine appellation
        "wd:Q282",     // wine
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun languageCode(tag: String): String {
        val primary = tag.lowercase().substringBefore('-').substringBefore('_')
        return when (primary) {
            "en", "fr", "de", "es", "it", "pt", "nl" -> primary
            else -> "fr"
        }
    }

    fun labelLanguages(primary: String): String {
        val code = languageCode(primary)
        return if (code == "en") "en,fr" else "$code,en"
    }

    fun escapeLiteral(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun qidFromUri(uri: String): String? {
        val id = uri.substringAfterLast('/').trim()
        return id.takeIf { it.matches(Regex("""Q\d+""")) }
    }

    /** Commons P18 value ? HTTPS Special:FilePath URL. */
    fun commonsImageUrl(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        if (v.startsWith("http://", ignoreCase = true) || v.startsWith("https://", ignoreCase = true)) {
            return v.replaceFirst("http://", "https://", ignoreCase = true)
        }
        val file = v.removePrefix("File:").removePrefix("file:")
        if (file.isEmpty()) return null
        return "https://commons.wikimedia.org/wiki/Special:FilePath/" +
            file.replace(" ", "_")
    }

    fun searchQuery(query: String, language: String, limit: Int = SEARCH_LIMIT): String {
        val q = escapeLiteral(query.trim())
        val lang = languageCode(language)
        val labels = labelLanguages(language)
        val types = wineRelatedClasses.joinToString(" ")
        return """
            SELECT DISTINCT ?item ?itemLabel ?itemDescription ?image WHERE {
              SERVICE wikibase:mwapi {
                bd:serviceParam wikibase:api "EntitySearch" .
                bd:serviceParam wikibase:endpoint "www.wikidata.org" .
                bd:serviceParam mwapi:search "$q" .
                bd:serviceParam mwapi:language "$lang" .
                bd:serviceParam wikibase:limit ${limit.coerceIn(1, 20)} .
                ?item wikibase:apiOutputItem mwapi:item .
              }
              ?item wdt:P31/wdt:P279* ?type .
              VALUES ?type { $types }
              OPTIONAL { ?item wdt:P18 ?image }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "$labels". }
            }
            LIMIT ${limit.coerceIn(1, 20)}
        """.trimIndent()
    }

    fun enrichQuery(qid: String, language: String): String {
        val id = qid.trim().uppercase().removePrefix("WD:")
        require(id.matches(Regex("""Q\d+"""))) { "invalid Wikidata id: $qid" }
        val labels = labelLanguages(language)
        return """
            SELECT ?itemLabel ?itemDescription ?countryLabel ?regionLabel ?grapeLabel WHERE {
              BIND(wd:$id AS ?item)
              OPTIONAL { ?item wdt:P17 ?country }
              OPTIONAL { ?item wdt:P131 ?region }
              OPTIONAL {
                ?item wdt:P186 ?grape .
                ?grape wdt:P31/wdt:P279* wd:Q36180 .
              }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "$labels". }
            }
        """.trimIndent()
    }

    fun listFrenchWineRegionsQuery(language: String, limit: Int = REGIONS_LIMIT): String {
        val labels = labelLanguages(language)
        return """
            SELECT DISTINCT ?item ?itemLabel ?itemDescription WHERE {
              ?item wdt:P31/wdt:P279* wd:Q2335854 .
              ?item wdt:P17 wd:Q142 .
              SERVICE wikibase:label { bd:serviceParam wikibase:language "$labels". }
            }
            ORDER BY ASC(?itemLabel)
            LIMIT ${limit.coerceIn(1, 500)}
        """.trimIndent()
    }

    fun parseSearchResults(jsonText: String): List<ProductInfo> {
        val root = runCatching { json.decodeFromString<SparqlResponse>(jsonText) }.getOrNull()
            ?: return emptyList()
        val seen = mutableSetOf<String>()
        return buildList {
            for (row in root.results.bindings) {
                val uri = row["item"]?.value ?: continue
                val qid = qidFromUri(uri) ?: continue
                if (!seen.add(qid)) continue
                val label = row["itemLabel"]?.value?.trim().orEmpty()
                if (label.isEmpty() || label == qid) continue
                val description = row["itemDescription"]?.value?.trim().orEmpty()
                add(
                    ProductInfo(
                        name = label,
                        brand = label,
                        country = "",
                        category = "",
                        imageUrl = row["image"]?.value?.let { commonsImageUrl(it) },
                        region = description.takeIf { it.isNotEmpty() },
                        source = DISPLAY_NAME,
                        externalId = qid,
                        externalSource = PROVIDER_ID,
                    ),
                )
            }
        }
    }

    fun parseEnrichment(jsonText: String): WineEnrichment? {
        val root = runCatching { json.decodeFromString<SparqlResponse>(jsonText) }.getOrNull()
            ?: return null
        val bindings = root.results.bindings
        if (bindings.isEmpty()) return null
        val first = bindings.first()
        val grapes = bindings.mapNotNull { it["grapeLabel"]?.value?.trim()?.takeIf { g -> g.isNotEmpty() } }
            .distinct()
        val description = first["itemDescription"]?.value?.trim().orEmpty()
        val country = first["countryLabel"]?.value?.trim().orEmpty()
        val region = first["regionLabel"]?.value?.trim().orEmpty()
        if (description.isEmpty() && country.isEmpty() && region.isEmpty() && grapes.isEmpty()) {
            // Still valid if we at least have a label (entity exists).
            val label = first["itemLabel"]?.value?.trim().orEmpty()
            if (label.isEmpty()) return null
        }
        return WineEnrichment(
            description = description,
            grapes = grapes,
            regionName = region,
            country = country,
            source = DISPLAY_NAME,
        )
    }

    fun parseRegions(jsonText: String): List<Region> {
        val root = runCatching { json.decodeFromString<SparqlResponse>(jsonText) }.getOrNull()
            ?: return emptyList()
        val seen = mutableSetOf<String>()
        return buildList {
            for (row in root.results.bindings) {
                val uri = row["item"]?.value ?: continue
                val qid = qidFromUri(uri) ?: continue
                if (!seen.add(qid)) continue
                val name = row["itemLabel"]?.value?.trim().orEmpty()
                if (name.isEmpty() || name == qid) continue
                add(
                    Region(
                        id = "wd-$qid",
                        name = name,
                        country = "France",
                        description = row["itemDescription"]?.value?.trim().orEmpty(),
                    ),
                )
            }
        }
    }
}

@Serializable
private data class SparqlResponse(
    val results: SparqlResults = SparqlResults(),
)

@Serializable
private data class SparqlResults(
    val bindings: List<Map<String, SparqlValue>> = emptyList(),
)

@Serializable
private data class SparqlValue(
    val type: String = "",
    val value: String = "",
    @SerialName("xml:lang") val lang: String? = null,
)
