package fr.geoking.vincent.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Thin MediaPipe wrapper around on-device Gemma. One engine at a time;
 * [generateJson] extracts the first `{…}` block from the model reply.
 */
object GemmaLlm {
    private const val TAG = "VincentGemma"
    private const val MAX_TOKENS = 1024

    private var appContext: Context? = null
    private var engine: LlmInference? = null
    private val mutex = Mutex()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }

    val isAvailable: Boolean
        get() = GemmaModel.isReady()

    private fun ensureEngine(): LlmInference? {
        val existing = engine
        if (existing != null) return existing
        val ctx = appContext ?: return null
        val path = GemmaModel.modelPath ?: return null
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(64)
                .build()
            LlmInference.createFromOptions(ctx, options).also { engine = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Gemma: ${e.message}", e)
            null
        }
    }

    /**
     * Runs [prompt] on-device and parses a JSON object from the reply.
     * Returns null when the model is missing, init fails, or JSON is invalid.
     */
    suspend fun generateJson(prompt: String): JSONObject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val llm = ensureEngine()
            if (llm == null) {
                AiUsage.recordGemmaUnavailable()
                return@withLock null
            }
            return@withLock try {
                AiUsage.recordGemmaText()
                val raw = llm.generateResponse(prompt)
                extractJson(raw)
            } catch (e: Exception) {
                Log.e(TAG, "Gemma generate failed: ${e.message}", e)
                close()
                null
            }
        }
    }

    private fun extractJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        // Prefer fenced ```json … ``` then first {…} span.
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(candidate.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }
}
