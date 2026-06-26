package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/** Persists a captured rack JPEG (the AR image target) and returns its absolute file path. */
interface RackImageSaver {
    suspend fun save(jpeg: ByteArray, rackId: String): String
}

@Composable
expect fun rememberRackImageSaver(): RackImageSaver
