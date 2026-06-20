package fr.geoking.vincent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.geoking.vincent.model.BottlePhotoKind
import java.io.File

@Composable
actual fun rememberLabelImageSaver(): LabelImageSaver {
    val context = LocalContext.current
    return remember(context) {
        object : LabelImageSaver {
            override suspend fun save(jpeg: ByteArray, bottleId: String, kind: BottlePhotoKind): String = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "labels").apply { mkdirs() }
                val safeId = bottleId.replace(Regex("[^a-zA-Z0-9_-]"), "-")
                val file = File(dir, "$safeId-${kind.suffix}.jpg")
                file.writeBytes(jpeg)
                file.absolutePath
            }
        }
    }
}
