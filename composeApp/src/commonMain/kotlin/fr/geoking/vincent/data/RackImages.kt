package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/** Persists a captured rack JPEG (the AR image target) and returns its absolute file path. */
interface RackImageSaver {
    suspend fun save(jpeg: ByteArray, rackId: String): String

    /** Persists a JPEG under an explicit [name] (e.g. a custom AR marker) and returns its path. */
    suspend fun saveNamed(jpeg: ByteArray, name: String): String
}

@Composable
expect fun rememberRackImageSaver(): RackImageSaver
