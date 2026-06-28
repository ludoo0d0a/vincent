package fr.geoking.vincent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberRackImageSaver(): RackImageSaver {
    val context = LocalContext.current
    return remember(context) {
        object : RackImageSaver {
            override suspend fun save(jpeg: ByteArray, rackId: String): String = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "racks").apply { mkdirs() }
                val safeId = rackId.replace(Regex("[^a-zA-Z0-9_-]"), "-").ifBlank { "rack" }
                val file = File(dir, "$safeId-ar.jpg")
                file.writeBytes(jpeg)
                file.absolutePath
            }

            override suspend fun saveNamed(jpeg: ByteArray, name: String): String = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "racks").apply { mkdirs() }
                val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "-").ifBlank { "marker" }
                val file = File(dir, "$safeName.jpg")
                file.writeBytes(jpeg)
                file.absolutePath
            }
        }
    }
}
