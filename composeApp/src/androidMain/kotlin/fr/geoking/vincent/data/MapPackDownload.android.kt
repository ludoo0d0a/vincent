package fr.geoking.vincent.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.debug.InternalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

private const val TAG = "MapPack"
private const val CONNECT_TIMEOUT_MS = 30_000
private const val READ_TIMEOUT_MS = 15 * 60_000 // large zip (~270 MiB)

private fun mapPackRoot(context: Context): File =
    File(context.filesDir, Appellations.MAP_PACK_DIR)

@Composable
actual fun rememberMapPackDownload(
    onLoading: (Boolean) -> Unit,
    onResult: (MapPackResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context) {
        {
            scope.launch {
                onLoading(true)
                val result = withContext(Dispatchers.IO) {
                    runCatching { downloadMapPack(context) }
                        .getOrElse { e ->
                            InternalLog.e(TAG, "Map pack download failed", e)
                            when (e) {
                                is MapPackException -> MapPackResult.Err(e.failure, e.httpCode)
                                is IOException -> MapPackResult.Err(MapPackFailure.Network)
                                else -> MapPackResult.Err(MapPackFailure.Storage)
                            }
                        }
                }
                onResult(result)
                onLoading(false)
            }
        }
    }
}

actual fun isMapPackInstalled(): Boolean = false

fun isMapPackInstalled(context: Context): Boolean {
    val dir = mapPackRoot(context)
    return dir.isDirectory && dir.listFiles()?.any { it.extension.equals("geojson", ignoreCase = true) } == true
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

private class MapPackException(
    val failure: MapPackFailure,
    val httpCode: Int? = null,
) : IOException("MapPack:$failure${httpCode?.let { ":$it" } ?: ""}")

private suspend fun downloadMapPack(context: Context): MapPackResult {
    val base = BuildConfig.AI_PROXY_URL.trim().removeSuffix("/")
    if (base.isBlank()) throw MapPackException(MapPackFailure.NotConfigured)

    val appCheckToken = try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
    } catch (e: Exception) {
        InternalLog.e(TAG, "App Check token unavailable", e)
        null
    }
    val idToken = try {
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
    } catch (e: Exception) {
        InternalLog.e(TAG, "ID token unavailable", e)
        null
    }
    if (idToken.isNullOrBlank()) {
        throw MapPackException(MapPackFailure.AuthRequired)
    }

    val url = "$base/v1/catalog/map-pack"
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/zip")
        appCheckToken?.let { setRequestProperty("X-Firebase-AppCheck", it) }
        setRequestProperty("Authorization", "Bearer $idToken")
    }

    val code = try {
        conn.responseCode
    } catch (e: IOException) {
        throw MapPackException(MapPackFailure.Network)
    }

    if (code !in 200..299) {
        conn.errorStream?.bufferedReader()?.use { it.readText().take(200) }?.let {
            InternalLog.e(TAG, "HTTP $code: $it")
        }
        val failure = when (code) {
            401, 403 -> MapPackFailure.AuthRequired
            404 -> MapPackFailure.NotFound
            else -> MapPackFailure.Http
        }
        throw MapPackException(failure, code)
    }

    val cacheDir = context.cacheDir
    val tempZip = File(cacheDir, "appellations-map-fr.download.zip")
    val tempDir = File(cacheDir, "appellations-map-fr.download").also {
        it.deleteRecursively()
        it.mkdirs()
    }
    try {
        conn.inputStream.use { input ->
            tempZip.outputStream().buffered(64 * 1024).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }

        var total = 0L
        tempZip.inputStream().buffered(64 * 1024).use { fileIn ->
            ZipInputStream(fileIn).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".geojson", ignoreCase = true)) {
                        val name = File(entry.name).name
                        if (name.isNotBlank() && !name.startsWith(".")) {
                            val out = File(tempDir, name)
                            out.outputStream().buffered(64 * 1024).use { zip.copyTo(it, bufferSize = 64 * 1024) }
                            total += out.length()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (total <= 0L) throw MapPackException(MapPackFailure.Empty)

        val root = mapPackRoot(context)
        if (root.exists()) root.deleteRecursively()
        if (!tempDir.renameTo(root)) {
            root.mkdirs()
            tempDir.listFiles()?.forEach { src ->
                val dest = File(root, src.name)
                if (!src.renameTo(dest)) {
                    src.copyTo(dest, overwrite = true)
                    src.delete()
                }
            }
            tempDir.deleteRecursively()
        }
        InternalLog.i(TAG, "Map pack installed: ${total / 1024} KiB")
        return MapPackResult.Ok(total)
    } catch (e: MapPackException) {
        throw e
    } catch (e: IOException) {
        InternalLog.e(TAG, "I/O while saving map pack", e)
        throw MapPackException(MapPackFailure.Storage)
    } finally {
        tempZip.delete()
        if (tempDir.exists()) tempDir.deleteRecursively()
        conn.disconnect()
    }
}
