package fr.geoking.vincent.ai

import android.util.Base64
import android.util.Log
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.debug.HttpDebug
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// GEMINI_API_KEY comes from local.properties (or CI env) via BuildConfig — never
// hardcoded. Get a free key at https://aistudio.google.com/apikey. Blank = no-op.
private const val MODEL = "gemini-2.5-flash"
private const val TAG = "VincentAI"

/** Single Gemini-backed client implementing both seams. */
object GeminiClient : WineRecognizer, PriceEstimator, FoodPairer {

    override suspend fun pairings(bottle: Bottle): List<String> = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} — ${bottle.color.label}, ${bottle.appellation}"
        val json = generate(
            langDirective() +
                "Propose 6 accords mets-vin concis (un plat chacun, 1–3 mots) pour ce vin. " +
                "JSON {pairings:[string]}. Vin: \"$q\"",
            imageB64 = null,
        ) ?: return@withContext emptyList()
        val arr = json.optJSONArray("pairings") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
    }

    override suspend fun fromText(title: String): RecognizeOutcome = withContext(Dispatchers.IO) {
        val json = generate(
            langDirective() +
                "Extrait les détails du vin en JSON {domain, appellation, color, region, vintage, category}. " +
                "color parmi rouge/blanc/rosé/pétillant. Titre: \"$title\"",
            imageB64 = null,
        ) ?: return@withContext RecognizeOutcome(error = lastError)
        val bottle = toBottle(json, "ia-${title.hashCode()}")
        if (bottle == null) RecognizeOutcome(error = "Impossible d'extraire les infos du texte.")
        else RecognizeOutcome(bottle = bottle)
    }

    override suspend fun fromImage(jpeg: ByteArray): RecognizeOutcome = withContext(Dispatchers.IO) {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val json = generate(
            langDirective() +
                "Lis l'étiquette de cette bouteille et renvoie JSON " +
                "{domain, appellation, color, region, vintage, category}.",
            imageB64 = b64,
        ) ?: return@withContext RecognizeOutcome(error = lastError)
        val bottle = toBottle(json, "ia-img-${jpeg.size}")
        if (bottle == null) RecognizeOutcome(error = "Aucun domaine détecté sur l'étiquette.")
        else RecognizeOutcome(bottle = bottle)
    }

    override suspend fun estimate(bottle: Bottle): PriceEstimate? = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} ${bottle.appellation}".trim()
        val json = generate(
            langDirective() +
                "Donne le prix marché estimé en euros (entier) pour ce vin, avec une source courte. " +
                "JSON {price:int, source:string}. Vin: \"$q\"",
            imageB64 = null,
        ) ?: return@withContext null
        val price = json.optInt("price", 0)
        if (price <= 0) null
        else PriceEstimate(price, json.optString("source", "estimation"), "estimation IA")
    }

    // Instructs Gemini to reply in the device's current language so user-facing
    // text (pairings, price source…) matches the rest of the localized UI.
    private fun langDirective(): String {
        val locale = Locale.getDefault()
        val name = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { locale.language }
        return "Respond in $name (locale \"${locale.language}\"). "
    }

    private var lastError: String? = null

    private fun fail(msg: String): JSONObject? {
        lastError = msg
        Log.e(TAG, msg)
        return null
    }

    private fun generate(prompt: String, imageB64: String?): JSONObject? {
        lastError = null
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return fail("Clé API Gemini manquante. Ajoutez GEMINI_API_KEY dans local.properties.")
        }
        if (!BuildConfig.GEMINI_API_KEY.startsWith("AIza")) {
            return fail(
                "Clé API au mauvais format (attendu AIzaSy… depuis aistudio.google.com/apikey).",
            )
        }
        val started = System.currentTimeMillis()
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent" +
                "?key=${BuildConfig.GEMINI_API_KEY}"
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
            val bodyText = body.toString()
            val logBody = if (imageB64 != null) {
                bodyText.replace(imageB64, "<image ${imageB64.length} chars>")
            } else {
                bodyText
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(bodyText.encodeToByteArray()) }
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Gemini HTTP $code: ${err?.take(400)}")
                HttpDebug.log(
                    label = "Gemini $MODEL",
                    method = "POST",
                    url = endpoint,
                    requestBody = logBody,
                    statusCode = code,
                    responseBody = err,
                    durationMs = elapsed,
                    error = geminiErrorDetail(err),
                )
                return fail(httpFailMessage(code, err))
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            HttpDebug.log(
                label = "Gemini $MODEL",
                method = "POST",
                url = endpoint,
                requestBody = logBody,
                statusCode = code,
                responseBody = resp,
                durationMs = elapsed,
            )
            val text = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
            JSONObject(text)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - started
            Log.e(TAG, "Gemini call failed: ${e.javaClass.simpleName}: ${e.message}", e)
            HttpDebug.log(
                label = "Gemini $MODEL",
                method = "POST",
                url = endpoint,
                durationMs = elapsed,
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
            fail("Identification impossible — réessayez ou passez en manuel.")
        }
    }

    private fun geminiErrorDetail(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val err = JSONObject(raw).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
                ?: err?.optString("status")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            raw.take(240)
        }
    }

    private fun httpFailMessage(code: Int, raw: String?): String {
        val detail = geminiErrorDetail(raw)?.let { " — $it" }.orEmpty()
        return when (code) {
            403 -> "Clé API invalide ou API non activée.$detail"
            404 -> "Modèle ou URL introuvable ($code). Vérifiez le modèle « $MODEL ».$detail"
            429 -> "Quota Gemini dépassé — réessayez plus tard."
            in 400..499 -> "Requête refusée par l'IA ($code)$detail"
            else -> "Identification impossible (erreur réseau $code)$detail"
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
