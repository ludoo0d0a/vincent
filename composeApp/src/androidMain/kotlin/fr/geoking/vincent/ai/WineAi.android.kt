package fr.geoking.vincent.ai

import android.util.Base64
import android.util.Log
import fr.geoking.vincent.data.ProductInfo
import fr.geoking.vincent.data.Settings
import fr.geoking.vincent.data.WineDataSource
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
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import vincent.composeapp.generated.resources.*

/**
 * Wine AI router: OCR/STT → [WineLabelParser] → Gemma on-device → optional Gemini BYOK
 * (user key in Settings). No BuildConfig Gemini key and no vision without a user key.
 */
private const val MODEL = "gemini-flash-latest"
private const val TAG = "VincentAI"
/** Below this, OCR is treated as empty → Gemma cannot help from pixels alone. */
private const val OCR_MIN_CHARS = 8

object WineAiEngine : WineRecognizer, PriceEstimator, PriceSearcher, FoodPairer {

    private fun geminiEnabled(): Boolean =
        Settings.geminiFallbackEnabled && Settings.geminiApiKey.isNotBlank()

    override fun search(bottle: Bottle): Flow<PriceSearchResult> = flow {
        val geminiLabel = getString(Res.string.price_source_gemini)
        estimate(bottle)?.let { est ->
            emit(PriceSearchResult(est.source.ifBlank { geminiLabel }, est.amountEur, "", true))
        }
        val links = bottlePriceCompareLinks(bottle)
        for (link in links) {
            val content = fetchText(link.url)
            if (content.isBlank()) {
                emit(PriceSearchResult(link.label, 0, link.url, false))
                continue
            }
            val q = "${bottle.domain} ${bottle.vintage} ${bottle.appellation}".trim()
            val json = generateJson(
                langDirective() +
                    "Dans le texte suivant (résultats de recherche), trouve le prix exact (entier en euros) " +
                    "et l'URL de la fiche produit pour ce vin : \"$q\". " +
                    "Renvoie JSON {price:int, url:string, found:boolean}. " +
                    "Si plusieurs résultats, prends le plus pertinent. " +
                    "Texte: \n\n$content",
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
            html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(8000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $url: ${e.message}")
            ""
        }
    }

    override suspend fun pairings(bottle: Bottle): List<String> = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} — ${colorToken(bottle.color)}, ${bottle.appellation}"
        val json = generateJson(
            langDirective() +
                "Propose 6 accords mets-vin concis (un plat chacun, 1–3 mots) pour ce vin. " +
                "JSON {pairings:[string]}. Vin: \"$q\"",
        ) ?: return@withContext emptyList()
        val arr = json.optJSONArray("pairings") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
    }

    override suspend fun fromText(title: String): RecognizeOutcome = withContext(Dispatchers.IO) {
        recognizeFromFreeText(
            title,
            localPath = AiPath.VOICE_LOCAL,
            source = AddSource.VOICE,
        )
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
        val prompt = langDirective() +
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
            "reply = une phrase courte confirmant ce qui a été complété ou demandant la donnée manquante."
        val json = generateJson(prompt)
            ?: return@withContext RecognizeOutcome(error = lastError ?: getString(Res.string.ai_error_gemma_unavailable))
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
        val compact = downscaleJpeg(jpeg)
        val ocrText = try {
            labelOcr().recognize(compact)
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed: ${e.message}")
            ""
        }
        if (ocrText.length < OCR_MIN_CHARS) {
            // No Gemma vision in v1. Optional Gemini vision only with BYOK.
            if (geminiEnabled()) return@withContext geminiFromImage(compact)
            return@withContext RecognizeOutcome(
                error = getString(Res.string.ai_error_ocr_empty),
                rawText = ocrText,
            )
        }
        recognizeFromFreeText(
            ocrText,
            localPath = AiPath.OCR_LOCAL,
            source = AddSource.SCAN,
        ).copy(rawText = ocrText)
    }

    /**
     * Shared path for voice transcripts and OCR text: local parse, catalogue suggestions,
     * then Gemma (then optional Gemini) when confidence is low.
     */
    private suspend fun recognizeFromFreeText(
        title: String,
        localPath: AiPath,
        source: AddSource,
    ): RecognizeOutcome {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return RecognizeOutcome(error = getString(Res.string.ai_error_extract_text))
        }
        val parsed = WineLabelParser.parse(trimmed)
        val query = parsed.searchQuery.ifBlank { trimmed }
        val suggestions = catalogueSuggestions(query)
        if (parsed.isConfident) {
            val bottle = bottleFromFields(parsed, "local-${trimmed.hashCode()}", source)
            if (bottle != null) {
                AiUsage.recordLocal(localPath)
                return RecognizeOutcome(bottle = bottle, suggestions = suggestions)
            }
        }
        return generativeFromText(trimmed, source, suggestions)
    }

    private suspend fun generativeFromText(
        title: String,
        source: AddSource,
        suggestions: List<ProductInfo>,
    ): RecognizeOutcome {
        val prompt = extractPrompt(title)
        val json = generateJson(prompt)
        if (json != null) {
            val bottle = toBottle(json, "ia-${title.hashCode()}")?.copy(source = source)
            if (bottle != null) {
                val more = suggestions.ifEmpty { catalogueSuggestions("${bottle.domain} ${bottle.appellation} ${bottle.vintage}") }
                return RecognizeOutcome(bottle = bottle, suggestions = more)
            }
        }
        return RecognizeOutcome(
            error = lastError ?: getString(Res.string.ai_error_extract_text),
            suggestions = suggestions,
        )
    }

    private fun extractPrompt(title: String): String =
        langDirective() +
            "Extrait les détails du vin en JSON {domain, appellation, color, region, vintage, category, alcohol, sugar, grapes, aging_potential, drink_from, drink_to}. " +
            "color parmi rouge/blanc/rosé/pétillant. " +
            "alcohol = nombre (ex: 13.5). " +
            "sugar parmi sec/demi-sec/moelleux. " +
            "grapes = liste de chaînes (ex: [\"Merlot\", \"Cabernet Sauvignon\"]). " +
            "aging_potential = nombre d'années de garde estimé (entier). " +
            "drink_from/drink_to = années de début/fin de consommation estimées. " +
            "Titre: \"$title\""

    private suspend fun geminiFromImage(jpeg: ByteArray): RecognizeOutcome {
        AiUsage.recordGeminiVision()
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val json = generateGemini(
            langDirective() +
                "Lis l'étiquette de cette bouteille et renvoie JSON " +
                "{domain, appellation, color, region, vintage, category, alcohol, sugar, grapes, aging_potential, drink_from, drink_to}. " +
                "aging_potential = nombre d'années de garde estimé (entier). " +
                "drink_from/drink_to = années de début/fin de consommation estimées. " +
                "alcohol = nombre (ex: 13.5). " +
                "sugar parmi sec/demi-sec/moelleux. " +
                "grapes = liste de chaînes (ex: [\"Merlot\", \"Cabernet Sauvignon\"]).",
            imageB64 = b64,
        ) ?: return RecognizeOutcome(error = lastError)
        val bottle = toBottle(json, "ia-img-${jpeg.size}")
        return if (bottle == null) RecognizeOutcome(error = getString(Res.string.ai_error_no_label))
        else RecognizeOutcome(
            bottle = bottle,
            suggestions = catalogueSuggestions("${bottle.domain} ${bottle.appellation} ${bottle.vintage}"),
        )
    }

    private suspend fun bottleFromFields(
        fields: WineLabelFields,
        id: String,
        source: AddSource,
    ): Bottle? {
        if (fields.domain.isBlank()) return null
        val region = fields.region
        val appellation = fields.appellation.ifBlank { region }
        return Bottle(
            id = id,
            domain = fields.domain,
            appellation = appellation,
            color = colorOf(fields.colorHint),
            category = categoryOf("$region $appellation ${fields.categoryHint}"),
            vintage = fields.vintage.ifBlank { "NM" },
            price = 0,
            quantity = 1,
            rating = 0.0,
            cellarSpot = "—",
            provenance = region.ifBlank { appellation },
            merchant = "—",
            purchaseDate = getString(Res.string.add_today),
            occasion = "—",
            alcoholLevel = fields.alcohol,
            sugarLevel = sugarOf(fields.sugarHint),
            grapes = fields.grapes,
            source = source,
            addedLabel = getString(Res.string.ai_added_label),
        )
    }

    private suspend fun catalogueSuggestions(query: String): List<ProductInfo> {
        if (query.isBlank()) return emptyList()
        return try {
            WineDataSource.search(query).take(5)
        } catch (e: Exception) {
            Log.w(TAG, "Catalogue search failed: ${e.message}")
            emptyList()
        }
    }

    fun mergeProduct(bottle: Bottle, hit: ProductInfo): Bottle =
        bottle.copy(
            domain = bottle.domain.ifBlank { hit.brand }.ifBlank { bottle.domain },
            appellation = bottle.appellation.ifBlank {
                hit.name.takeIf { it.isNotBlank() && !it.equals(hit.brand, ignoreCase = true) }.orEmpty()
            }.ifBlank { bottle.appellation },
            provenance = bottle.provenance.ifBlank { hit.region.orEmpty() }.ifBlank { bottle.provenance },
            vintage = bottle.vintage.takeUnless { it == "NM" }
                ?: hit.vintage?.takeIf { it.isNotBlank() }
                ?: bottle.vintage,
            grapes = bottle.grapes.ifEmpty {
                hit.grape?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
            },
        )

    override suspend fun estimate(bottle: Bottle): PriceEstimate? = withContext(Dispatchers.IO) {
        val q = "${bottle.domain} ${bottle.vintage} ${bottle.appellation}".trim()
        val json = generateJson(
            langDirective() +
                "Donne le prix marché estimé en euros (entier) pour ce vin. " +
                "JSON {price:int}. Vin: \"$q\"",
        ) ?: return@withContext null
        val price = json.optInt("price", 0)
        if (price <= 0) return@withContext null
        val source = when {
            AiUsage.lastPath == AiPath.GEMMA_TEXT -> getString(Res.string.price_source_gemma)
            else -> getString(Res.string.price_source_gemini)
        }
        PriceEstimate(price, source, source)
    }

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

    /**
     * Gemma first when the model is ready; else Gemini if BYOK is enabled.
     * Text-only (no image).
     */
    private suspend fun generateJson(prompt: String): JSONObject? {
        lastError = null
        if (GemmaLlm.isAvailable) {
            val json = GemmaLlm.generateJson(prompt)
            if (json != null) return json
            Log.w(TAG, "Gemma returned no JSON — trying Gemini if enabled")
        } else {
            AiUsage.recordGemmaUnavailable()
        }
        if (!geminiEnabled()) {
            return fail(
                if (!GemmaModel.isReady()) getString(Res.string.ai_error_gemma_unavailable)
                else getString(Res.string.ai_error_extract_text),
            )
        }
        AiUsage.recordGeminiText()
        return generateGemini(prompt, imageB64 = null)
    }

    private suspend fun generateGemini(prompt: String, imageB64: String?): JSONObject? {
        lastError = null
        val key = Settings.geminiApiKey.trim()
        if (key.isBlank()) {
            return fail(getString(Res.string.ai_error_no_key))
        }
        val started = System.currentTimeMillis()
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$key"
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
                    url = endpoint.substringBefore("?"),
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
                url = endpoint.substringBefore("?"),
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
                url = endpoint.substringBefore("?"),
                durationMs = elapsed,
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
            fail(getString(Res.string.ai_error_generic))
        }
    }

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
        val detail = geminiErrorDetail(raw)?.let { " — $it" }.orEmpty()
        return when (code) {
            403 -> getString(Res.string.ai_error_http_403, detail)
            404 -> getString(Res.string.ai_error_http_404, code, detail)
            429 -> getString(Res.string.ai_error_http_429)
            in 400..499 -> getString(Res.string.ai_error_http_4xx, code, detail)
            else -> getString(Res.string.ai_error_http_other, code, detail)
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

actual fun wineRecognizer(): WineRecognizer = WineAiEngine
actual fun priceEstimator(): PriceEstimator = WineAiEngine
actual fun priceSearcher(): PriceSearcher = WineAiEngine
actual fun foodPairer(): FoodPairer = WineAiEngine
