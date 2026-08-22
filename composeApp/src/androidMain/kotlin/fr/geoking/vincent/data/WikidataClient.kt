package fr.geoking.vincent.data

import fr.geoking.vincent.debug.HttpDebug
import fr.geoking.vincent.model.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP client for Wikidata SPARQL ([WikidataSparql.ENDPOINT]). */
object WikidataClient {

    suspend fun search(query: String, language: String): List<ProductInfo> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val sparql = WikidataSparql.searchQuery(q, language)
        val body = execute(sparql, "search") ?: return@withContext emptyList()
        WikidataSparql.parseSearchResults(body)
    }

    suspend fun enrich(qid: String, language: String): WineEnrichment? = withContext(Dispatchers.IO) {
        val id = qid.trim()
        if (!id.matches(Regex("""Q\d+""", RegexOption.IGNORE_CASE))) return@withContext null
        val sparql = WikidataSparql.enrichQuery(id, language)
        val body = execute(sparql, "enrich") ?: return@withContext null
        WikidataSparql.parseEnrichment(body)
    }

    suspend fun listFrenchWineRegions(language: String): List<Region> = withContext(Dispatchers.IO) {
        val sparql = WikidataSparql.listFrenchWineRegionsQuery(language)
        val body = execute(sparql, "listRegions") ?: return@withContext emptyList()
        WikidataSparql.parseRegions(body)
    }

    private fun execute(sparql: String, op: String): String? {
        val urlStr = WikidataSparql.ENDPOINT +
            "?query=" + URLEncoder.encode(sparql, "UTF-8") +
            "&format=json"
        val started = System.currentTimeMillis()
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/sparql-results+json")
                setRequestProperty("User-Agent", WikidataSparql.USER_AGENT)
            }
            val status = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                HttpDebug.log(
                    label = WikidataSparql.DISPLAY_NAME,
                    method = "GET",
                    url = "${WikidataSparql.ENDPOINT}?op=$op",
                    statusCode = status,
                    responseBody = err?.take(500),
                    durationMs = elapsed,
                )
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(
                label = WikidataSparql.DISPLAY_NAME,
                method = "GET",
                url = "${WikidataSparql.ENDPOINT}?op=$op",
                statusCode = status,
                responseBody = body.take(500),
                durationMs = elapsed,
            )
            body
        } catch (e: Exception) {
            HttpDebug.log(
                label = WikidataSparql.DISPLAY_NAME,
                method = "GET",
                url = "${WikidataSparql.ENDPOINT}?op=$op",
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
            null
        }
    }
}
