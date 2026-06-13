package com.geoking.vincent.ai

import android.util.Base64
import com.geoking.vincent.BuildConfig
import com.geoking.vincent.model.AddSource
import com.geoking.vincent.model.Bottle
import com.geoking.vincent.model.WineCategory
import com.geoking.vincent.model.WineColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// GEMINI_API_KEY comes from local.properties (or CI env) via BuildConfig — never
// hardcoded. Get a free key at https://aistudio.google.com/apikey. Blank = no-op.
private const val MODEL = "gemini-2.0-flash"

/** Single Gemini-backed client implementing both seams. */
object GeminiClient : WineRecognizer, PriceEstimator, FoodPairer {

    override suspend fun pairings(bottle: Bottle): List<String> = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} — ${bottle.color.label}, ${bottle.appellation}"
        val json = generate(
            "Propose 6 accords mets-vin concis (un plat chacun, 1–3 mots) pour ce vin. " +
                "JSON {pairings:[string]}. Vin: \"$q\"",
            imageB64 = null,
        ) ?: return@withContext emptyList()
        val arr = json.optJSONArray("pairings") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
    }

    override suspend fun fromText(title: String): Bottle? = withContext(Dispatchers.IO) {
        val json = generate(
            "Extrait les détails du vin en JSON {domain, appellation, color, region, vintage, category}. " +
                "color parmi rouge/blanc/rosé/pétillant. Titre: \"$title\"",
            imageB64 = null,
        )
        json?.let { toBottle(it, "ia-${title.hashCode()}") }
    }

    override suspend fun fromImage(jpeg: ByteArray): Bottle? = withContext(Dispatchers.IO) {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val json = generate(
            "Lis l'étiquette de cette bouteille et renvoie JSON " +
                "{domain, appellation, color, region, vintage, category}.",
            imageB64 = b64,
        )
        json?.let { toBottle(it, "ia-img-${jpeg.size}") }
    }

    override suspend fun estimate(bottle: Bottle): PriceEstimate? = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} ${bottle.appellation}".trim()
        val json = generate(
            "Donne le prix marché estimé en euros (entier) pour ce vin, avec une source courte. " +
                "JSON {price:int, source:string}. Vin: \"$q\"",
            imageB64 = null,
        ) ?: return@withContext null
        val price = json.optInt("price", 0)
        if (price <= 0) null
        else PriceEstimate(price, json.optString("source", "estimation"), "estimation IA")
    }

    private fun generate(prompt: String, imageB64: String?): JSONObject? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return null
        return try {
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            if (imageB64 != null) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", "image/jpeg").put("data", imageB64),
                    ),
                )
            }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().encodeToByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val text = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }

    private fun toBottle(j: JSONObject, id: String): Bottle? {
        val domain = j.optString("domain").trim()
        if (domain.isEmpty()) return null
        val region = j.optString("region")
        val appellation = j.optString("appellation").ifBlank { region }
        return Bottle(
            id = id,
            domain = domain,
            appellation = appellation,
            color = colorOf(j.optString("color")),
            category = categoryOf("$region $appellation ${j.optString("category")}"),
            vintage = j.optString("vintage").ifBlank { "NM" },
            price = 0,
            quantity = 1,
            rating = 0.0,
            cellarSpot = "—",
            provenance = region.ifBlank { appellation },
            merchant = "—",
            purchaseDate = "Aujourd'hui",
            occasion = "—",
            source = AddSource.SCAN,
            addedLabel = "IA",
        )
    }

    private fun colorOf(raw: String): WineColor {
        val v = raw.lowercase()
        return when {
            "ros" in v -> WineColor.ROSE
            "blanc" in v || "white" in v -> WineColor.WHITE
            "pétill" in v || "petill" in v || "spark" in v || "champ" in v -> WineColor.SPARKLING
            else -> WineColor.RED
        }
    }

    private fun categoryOf(text: String): WineCategory {
        val v = text.lowercase()
        return when {
            "bourgogne" in v || "burgundy" in v || "chablis" in v -> WineCategory.BOURGOGNE
            "rhône" in v || "rhone" in v -> WineCategory.RHONE
            "provence" in v || "bandol" in v -> WineCategory.PROVENCE
            "loire" in v || "sancerre" in v -> WineCategory.LOIRE
            "champagne" in v || "reims" in v -> WineCategory.CHAMPAGNE
            else -> WineCategory.BORDEAUX
        }
    }
}

actual fun wineRecognizer(): WineRecognizer = GeminiClient
actual fun priceEstimator(): PriceEstimator = GeminiClient
actual fun foodPairer(): FoodPairer = GeminiClient
