package fr.geoking.vincent.ai

import android.util.Base64
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.data.bottlePriceCompareLinks
import fr.geoking.vincent.debug.HttpDebug
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.SugarLevel
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import vincent.composeapp.generated.resources.*

// AI calls go through the Cloudflare Worker proxy (BuildConfig.AI_PROXY_URL): the
// Gemini key stays server-side and each call carries an App Check token + the user's
// Firebase ID token. Debug builds without a proxy fall back to a direct Gemini call
// using BuildConfig.GEMINI_API_KEY (blank in release → no key ever ships in the APK).
private const val MODEL = "gemini-flash-latest"
private const val TAG = "VincentAI"

/** Single Gemini-backed client implementing both seams. */
object GeminiClient : WineRecognizer, PriceEstimator, PriceSearcher, FoodPairer {

    override fun search(bottle: Bottle): Flow<PriceSearchResult> = flow {
        val geminiLabel = getString(Res.string.price_source_gemini)
        estimate(bottle)?.let { est ->
            emit(PriceSearchResult(geminiLabel, est.amountEur, "", true))
        }
        val links = bottlePriceCompareLinks(bottle)
        for (link in links) {
            val content = fetchText(link.url)
            if (content.isBlank()) {
                emit(PriceSearchResult(link.label, 0, link.url, false))
                continue
            }
            val q = "${bottle.domain} ${bottle.vintage} ${bottle.appellation}".trim()
            val json = generate(
                langDirective() +
                        "Dans le texte suivant (résultats de recherche), trouve le prix exact (entier en euros) " +
                        "et l'URL de la fiche produit pour ce vin : \"$q\". " +
                        "Renvoie JSON {price:int, url:string, found:boolean}. " +
                        "Si plusieurs résultats, prends le plus pertinent. " +
                        "Texte: \n\n$content",
                imageB64 = null,
            )
            if (json != null && json.optBoolean("found", false)) {
                emit(
                    PriceSearchResult(
                        label = link.label,
                        price = json.optInt("price", 0),
                        url = json.optString("url", link.url),
                        isFound = true,
                    ),
                )
            } else {
                emit(PriceSearchResult(link.label, 0, link.url, false))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchText(url: String): String {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            if (conn.responseCode !in 200..299) return ""
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            // Basic HTML to Text: remove scripts, styles and tags
            html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(8000) // Gemini context limit safety
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $url: ${e.message}")
            ""
        }
    }

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
                "Extrait les détails du vin en JSON {domain, appellation, color, region, vintage, category, alcohol, sugar, grapes, aging_potential, drink_from, drink_to}. " +
                "color parmi rouge/blanc/rosé/pétillant. " +
                "alcohol = nombre (ex: 13.5). " +
                "sugar parmi sec/demi-sec/moelleux. " +
                "grapes = liste de chaînes (ex: [\"Merlot\", \"Cabernet Sauvignon\"]). " +
                "aging_potential = nombre d'années de garde estimé (entier). " +
                "drink_from/drink_to = années de début/fin de consommation estimées. " +
                "Titre: \"$title\"",
            imageB64 = null,
        ) ?: return@withContext RecognizeOutcome(error = lastError)
        val bottle = toBottle(json, "ia-${title.hashCode()}")
        if (bottle == null) RecognizeOutcome(error = getString(Res.string.ai_error_extract_text))
        else RecognizeOutcome(bottle = bottle)
    }

    override suspend fun refine(current: Bottle, instruction: String): RecognizeOutcome = withContext(Dispatchers.IO) {
        val ctx = JSONObject()
            .put("domain", current.domain)
            .put("appellation", current.appellation)
            .put("color", colorToken(current.color))
            .put("region", current.provenance)
            .put("vintage", current.vintage)
            .put("price", current.price)
            .put("alcohol", current.alcoholLevel)
            .put("sugar", sugarToken(current.sugarLevel))
        val json = generate(
            langDirective() +
                "Tu aides à compléter la fiche d'un vin par la discussion. " +
                "Fiche actuelle (JSON): $ctx. " +
                "Précision de l'utilisateur : \"$instruction\". " +
                "Renvoie la fiche mise à jour en JSON " +
                "{domain, appellation, color, region, vintage, category, price, alcohol, sugar, grapes, aging_potential, drink_from, drink_to, reply}. " +
                "aging_potential = nombre d'années de garde estimé (entier). " +
                "drink_from/drink_to = années de début/fin de consommation estimées. " +
                "color parmi rouge/blanc/rosé/pétillant. " +
                "price = prix unitaire en euros (entier, 0 si inconnu). " +
                "vintage = année sur 4 chiffres ou \"NM\" si non millésimé. " +
                "alcohol = nombre (ex: 13.5). " +
                "sugar parmi sec/demi-sec/moelleux. " +
                "grapes = liste de chaînes (ex: [\"Merlot\", \"Cabernet Sauvignon\"]). " +
                "Conserve les valeurs déjà connues si l'utilisateur ne les change pas. " +
                "reply = une phrase courte confirmant ce qui a été complété ou demandant la donnée manquante.",
            imageB64 = null,
        ) ?: return@withContext RecognizeOutcome(error = lastError)
        val reply = json.str("reply").takeIf { it.isNotEmpty() }
        val updated = toBottle(json, current.id)?.copy(
            price = json.optInt("price", current.price).takeIf { it > 0 } ?: current.price,
            alcoholLevel = if (json.has("alcohol")) json.optDouble("alcohol") else current.alcoholLevel,
            sugarLevel = if (json.has("sugar")) sugarOf(json.str("sugar")) else current.sugarLevel,
            grapes = json.optJSONArray("grapes")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: current.grapes,
            agingPotential = if (json.has("aging_potential")) json.optInt("aging_potential", current.agingPotential) else current.agingPotential,
            drinkFrom = if (json.has("drink_from")) json.optInt("drink_from", current.drinkFrom) else current.drinkFrom,
            drinkTo = if (json.has("drink_to")) json.optInt("drink_to", current.drinkTo) else current.drinkTo,
            quantity = current.quantity,
            cellarSpot = current.cellarSpot,
            source = current.source,
            photoBottle = current.photoBottle,
            photoLabel = current.photoLabel,
            photoBack = current.photoBack,
        )
        if (updated == null) RecognizeOutcome(error = getString(Res.string.ai_error_extract_text), reply = reply)
        else RecognizeOutcome(bottle = updated, reply = reply)
    }

    override suspend fun fromImage(jpeg: ByteArray): RecognizeOutcome = withContext(Dispatchers.IO) {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val json = generate(
            langDirective() +
                "Lis l'étiquette de cette bouteille et renvoie JSON " +
                "{domain, appellation, color, region, vintage, category, alcohol, sugar, grapes, aging_potential, drink_from, drink_to}. " +
                "aging_potential = nombre d'années de garde estimé (entier). " +
                "drink_from/drink_to = années de début/fin de consommation estimées. " +
                "alcohol = nombre (ex: 13.5). " +
                "sugar parmi sec/demi-sec/moelleux. " +
                "grapes = liste de chaînes (ex: [\"Merlot\", \"Cabernet Sauvignon\"]).",
            imageB64 = b64,
        ) ?: return@withContext RecognizeOutcome(error = lastError)
        val bottle = toBottle(json, "ia-img-${jpeg.size}")
        if (bottle == null) RecognizeOutcome(error = getString(Res.string.ai_error_no_label))
        else RecognizeOutcome(bottle = bottle)
    }

    override suspend fun estimate(bottle: Bottle): PriceEstimate? = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} ${bottle.appellation}".trim()
        val source = getString(Res.string.price_source_gemini)
        val json = generate(
            langDirective() +
                "Donne le prix marché estimé en euros (entier) pour ce vin. " +
                "JSON {price:int}. Vin: \"$q\"",
            imageB64 = null,
        ) ?: return@withContext null
        val price = json.optInt("price", 0)
        if (price <= 0) null
        else PriceEstimate(price, source, source)
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

    // The proxy reports the user's remaining daily allowance via these headers
    // (present on success, cache hits and the 429 quota-exceeded response).
    private fun readQuotaHeaders(conn: HttpURLConnection) {
        val remaining = conn.getHeaderField("X-AI-Quota-Remaining")?.toIntOrNull() ?: return
        val limit = conn.getHeaderField("X-AI-Quota-Limit")?.toIntOrNull() ?: return
        AiUsage.update(remaining = remaining, limit = limit)
    }

    private suspend fun generate(prompt: String, imageB64: String?): JSONObject? {
        lastError = null
        val proxyUrl = BuildConfig.AI_PROXY_URL
        return if (proxyUrl.isNotBlank()) {
            generateViaProxy(proxyUrl, prompt, imageB64)
        } else {
            generateDirect(prompt, imageB64)
        }
    }

    // Production path: POST to the Cloudflare Worker proxy. The Gemini key stays
    // server-side; the call is authenticated with an App Check token and the user's
    // Firebase ID token. The Worker returns the raw Gemini response, parsed below.
    private suspend fun generateViaProxy(
        proxyUrl: String,
        prompt: String,
        imageB64: String?,
    ): JSONObject? {
        val appCheckToken = try {
            FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        } catch (e: Exception) {
            Log.w(TAG, "App Check token unavailable: ${e.message}")
            ""
        }
        val idToken = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.w(TAG, "ID token unavailable: ${e.message}")
            null
        }
        if (idToken.isNullOrBlank()) {
            return fail(getString(Res.string.ai_error_sign_in))
        }
        val started = System.currentTimeMillis()
        return try {
            val body = JSONObject()
                .put("prompt", prompt)
                .put("responseMimeType", "application/json")
                .put("cacheable", imageB64 == null)
            if (imageB64 != null) body.put("imageB64", imageB64)
            val bodyText = body.toString()
            val logBody = if (imageB64 != null) {
                bodyText.replace(imageB64, "<image ${imageB64.length} chars>")
            } else {
                bodyText
            }

            val conn = (URL(proxyUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("X-Firebase-AppCheck", appCheckToken)
            }
            conn.outputStream.use { it.write(bodyText.encodeToByteArray()) }
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - started
            readQuotaHeaders(conn)
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "AI proxy HTTP $code: ${err?.take(400)}")
                HttpDebug.log(
                    label = "AI proxy",
                    method = "POST",
                    url = proxyUrl,
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
                label = "AI proxy",
                method = "POST",
                url = proxyUrl,
                requestBody = logBody,
                statusCode = code,
                responseBody = resp,
                durationMs = elapsed,
            )
            parseModelJson(resp)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - started
            Log.e(TAG, "AI proxy call failed: ${e.javaClass.simpleName}: ${e.message}", e)
            HttpDebug.log(
                label = "AI proxy",
                method = "POST",
                url = proxyUrl,
                durationMs = elapsed,
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
            fail(getString(Res.string.ai_error_generic))
        }
    }

    // Dev fallback (debug builds without a configured proxy): call Gemini directly
    // with BuildConfig.GEMINI_API_KEY. Never reached in release (key is blank there).
    private suspend fun generateDirect(prompt: String, imageB64: String?): JSONObject? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return fail(getString(Res.string.ai_error_no_key))
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
            parseModelJson(resp)
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
            fail(getString(Res.string.ai_error_generic))
        }
    }

    // The proxy and the direct call both return the raw Gemini generateContent
    // response; the model's JSON answer is the text of the first candidate part.
    private fun parseModelJson(resp: String): JSONObject {
        val text = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
        return JSONObject(text)
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

    private suspend fun httpFailMessage(code: Int, raw: String?): String {
        // The proxy tags its own 429s with a reason so we can show a tailored,
        // friendlier message instead of the generic Gemini-quota one.
        if (code == 429) {
            when (proxyQuotaReason(raw)) {
                "burst" -> return getString(Res.string.ai_error_quota_burst)
                "global" -> return getString(Res.string.ai_error_quota_global)
                "daily" -> AiUsage.quota?.limit?.let {
                    return getString(Res.string.ai_error_quota_daily, it)
                }
            }
        }
        val detail = geminiErrorDetail(raw)?.let { " — $it" }.orEmpty()
        return when (code) {
            403 -> getString(Res.string.ai_error_http_403, detail)
            404 -> getString(Res.string.ai_error_http_404, code, detail)
            429 -> getString(Res.string.ai_error_http_429)
            in 400..499 -> getString(Res.string.ai_error_http_4xx, code, detail)
            else -> getString(Res.string.ai_error_http_other, code, detail)
        }
    }

    // A RESOURCE_EXHAUSTED 429 from our proxy carries error.reason; null for
    // upstream/Gemini 429s (which fall back to the generic message).
    private fun proxyQuotaReason(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val err = JSONObject(raw).optJSONObject("error") ?: return null
            if (err.optString("status") != "RESOURCE_EXHAUSTED") return null
            err.optString("reason").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.str(key: String): String {
        val v = optString(key, "").trim()
        return if (v == "null") "" else v
    }

    private suspend fun toBottle(j: JSONObject, id: String): Bottle? {
        val domain = j.str("domain")
        if (domain.isEmpty()) return null
        val region = j.str("region")
        val appellation = j.str("appellation").ifBlank { region }
        return Bottle(
            id = id,
            domain = domain,
            appellation = appellation,
            color = colorOf(j.str("color")),
            category = categoryOf("$region $appellation ${j.str("category")}"),
            vintage = j.str("vintage").ifBlank { "NM" },
            price = 0,
            quantity = 1,
            rating = 0.0,
            cellarSpot = "—",
            provenance = region.ifBlank { appellation },
            merchant = "—",
            purchaseDate = getString(Res.string.add_today),
            occasion = "—",
            alcoholLevel = j.optDouble("alcohol", 0.0),
            sugarLevel = sugarOf(j.str("sugar")),
            grapes = j.optJSONArray("grapes")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            drinkFrom = j.optInt("drink_from", 0),
            drinkTo = j.optInt("drink_to", 0),
            agingPotential = j.optInt("aging_potential", 0),
            source = AddSource.SCAN,
            addedLabel = getString(Res.string.ai_added_label),
        )
    }

    // Stable French token sent back to Gemini so a refinement keeps the same colour
    // vocabulary it was asked to produce (decoupled from the localized UI label).
    private fun colorToken(color: WineColor): String = when (color) {
        WineColor.RED -> "rouge"
        WineColor.WHITE -> "blanc"
        WineColor.ROSE -> "rosé"
        WineColor.SPARKLING -> "pétillant"
    }

    private fun sugarToken(sugar: SugarLevel): String = when (sugar) {
        SugarLevel.SEC -> "sec"
        SugarLevel.DEMI_SEC -> "demi-sec"
        SugarLevel.MOELLEUX -> "moelleux"
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

    private fun sugarOf(raw: String): SugarLevel {
        val v = raw.lowercase()
        return when {
            "demi" in v -> SugarLevel.DEMI_SEC
            "moel" in v || "doux" in v || "liquor" in v -> SugarLevel.MOELLEUX
            else -> SugarLevel.SEC
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
actual fun priceSearcher(): PriceSearcher = GeminiClient
actual fun foodPairer(): FoodPairer = GeminiClient
