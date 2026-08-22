package fr.geoking.vincent.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import fr.geoking.vincent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

private fun mapPackRoot(context: Context): File =
    File(context.filesDir, Appellations.MAP_PACK_DIR).also { it.mkdirs() }

@Composable
actual fun rememberMapPackDownload(onLoading: (Boolean) -> Unit, onResult: (Long?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context) {
        {
            scope.launch {
                onLoading(true)
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { downloadMapPack(context) }.getOrNull()
                }
                onResult(bytes)
                onLoading(false)
            }
        }
    }
}

actual fun isMapPackInstalled(): Boolean {
    // Use isMapPackInstalled(context) from composables; this stub is for non-composable callers.
    return false
}

fun isMapPackInstalled(context: Context): Boolean {
    val dir = mapPackRoot(context)
    return dir.exists() && dir.listFiles()?.any { it.extension == "geojson" } == true
}

actual suspend fun readAppellationGeoJson(geoAsset: String): String? = withContext(Dispatchers.IO) {
    null // Android uses context-aware overload below.
}

suspend fun readAppellationGeoJson(context: Context, geoAsset: String): String? = withContext(Dispatchers.IO) {
    if (geoAsset.isBlank()) return@withContext null
    val file = File(mapPackRoot(context), geoAsset)
    if (!file.exists()) return@withContext null
    runCatching { file.readText() }.getOrNull()
}

private suspend fun downloadMapPack(context: Context): Long? {
    val base = BuildConfig.AI_PROXY_URL.trim().removeSuffix("/")
    if (base.isBlank()) return null
    val appCheckToken = try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
    } catch (_: Exception) {
        null
    }
    val idToken = try {
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
    } catch (_: Exception) {
        null
    }
    val conn = (URL("$base/v1/catalog/map-pack").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 30_000
        readTimeout = 120_000
        appCheckToken?.let { setRequestProperty("X-Firebase-AppCheck", it) }
        idToken?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    if (conn.responseCode !in 200..299) return null
    val root = mapPackRoot(context)
    root.listFiles()?.forEach { it.delete() }
    var total = 0L
    conn.inputStream.use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".geojson", ignoreCase = true)) {
                    val out = File(root, File(entry.name).name)
                    out.outputStream().use { zip.copyTo(it) }
                    total += out.length()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
    return if (total > 0) total else null
}
