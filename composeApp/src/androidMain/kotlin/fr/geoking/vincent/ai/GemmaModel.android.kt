package fr.geoking.vincent.ai

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and tracks Gemma 3 1B INT4 (`.task`) for MediaPipe LLM Inference.
 * Default URL is Hugging Face (gated — optional [Settings.huggingFaceToken]).
 * Override with BuildConfig.GEMMA_MODEL_URL for a self-hosted CDN.
 */
actual object GemmaModel {
    private const val TAG = "VincentGemma"
    private const val MODEL_FILE = "gemma3-1b-it-int4.task"
    private const val DEFAULT_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

    private var appContext: Context? = null

    private var _state by mutableStateOf<GemmaModelState>(GemmaModelState.Missing)
    actual val state: GemmaModelState get() = _state

    actual val modelPath: String?
        get() {
            val f = modelFile() ?: return null
            return if (f.exists() && f.length() > 1_000_000L) f.absolutePath else null
        }

    fun init(context: Context) {
        appContext = context.applicationContext
        refreshState()
    }

    actual fun isReady(): Boolean = _state is GemmaModelState.Ready && modelPath != null

    fun refreshState() {
        val path = modelPath
        _state = if (path != null) GemmaModelState.Ready else GemmaModelState.Missing
    }

    private fun modelFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, "models/$MODEL_FILE")
    }

    private fun downloadUrl(): String =
        BuildConfig.GEMMA_MODEL_URL.trim().ifBlank { DEFAULT_URL }

    actual suspend fun download() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: run {
            _state = GemmaModelState.Error("Context unavailable")
            return@withContext
        }
        if (_state is GemmaModelState.Downloading) return@withContext
        val dest = File(ctx.filesDir, "models/$MODEL_FILE")
        dest.parentFile?.mkdirs()
        val partial = File(dest.parentFile, "$MODEL_FILE.partial")
        _state = GemmaModelState.Downloading(0f)
        try {
            var url = downloadUrl()
            var redirects = 0
            while (redirects < 5) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "VincentAndroid/1.0")
                    val token = Settings.huggingFaceToken.trim()
                    if (token.isNotEmpty()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                val code = conn.responseCode
                when (code) {
                    in 300..399 -> {
                        val next = conn.getHeaderField("Location") ?: break
                        url = if (next.startsWith("http")) next else URL(URL(url), next).toString()
                        redirects++
                        continue
                    }
                    401, 403 -> {
                        _state = GemmaModelState.Error(
                            "Téléchargement refusé ($code). Ajoutez un jeton Hugging Face dans Réglages, " +
                                "ou hébergez le modèle (GEMMA_MODEL_URL).",
                        )
                        return@withContext
                    }
                    !in 200..299 -> {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
                        _state = GemmaModelState.Error("HTTP $code${err?.let { ": $it" } ?: ""}")
                        return@withContext
                    }
                }
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                partial.delete()
                FileOutputStream(partial).use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                _state = GemmaModelState.Downloading((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (!partial.renameTo(dest)) {
                    partial.copyTo(dest, overwrite = true)
                    partial.delete()
                }
                if (dest.length() < 1_000_000L) {
                    dest.delete()
                    _state = GemmaModelState.Error("Fichier modèle trop petit ou corrompu")
                    return@withContext
                }
                Log.i(TAG, "Gemma model ready: ${dest.absolutePath} (${dest.length()} bytes)")
                _state = GemmaModelState.Ready
                return@withContext
            }
            _state = GemmaModelState.Error("Trop de redirections")
        } catch (e: Exception) {
            Log.e(TAG, "Gemma download failed: ${e.message}", e)
            partial.delete()
            _state = GemmaModelState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    actual fun delete() {
        modelFile()?.delete()
        File(appContext?.filesDir, "models/$MODEL_FILE.partial").delete()
        GemmaLlm.close()
        _state = GemmaModelState.Missing
    }
}
