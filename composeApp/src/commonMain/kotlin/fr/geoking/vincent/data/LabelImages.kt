package fr.geoking.vincent.data

import androidx.compose.runtime.Composable
import fr.geoking.vincent.model.BottlePhotoKind

/** Persists a captured bottle JPEG and returns its absolute file path. */
interface LabelImageSaver {
    suspend fun save(jpeg: ByteArray, bottleId: String, kind: BottlePhotoKind = BottlePhotoKind.LABEL): String
}

@Composable
expect fun rememberLabelImageSaver(): LabelImageSaver
