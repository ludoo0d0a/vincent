package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/** Outcome of a France appellations map-pack download. */
sealed class MapPackResult {
    data class Ok(val bytes: Long) : MapPackResult()
    data class Err(val failure: MapPackFailure, val httpCode: Int? = null) : MapPackResult()
}

enum class MapPackFailure {
    NotConfigured,
    AuthRequired,
    NotFound,
    Http,
    Network,
    Empty,
    Storage,
}

/** Downloads the optional French appellations GeoJSON map pack from the Worker. */
@Composable
expect fun rememberMapPackDownload(
    onLoading: (Boolean) -> Unit,
    onResult: (MapPackResult) -> Unit,
): () -> Unit

/** Whether the map pack is present on device. */
expect fun isMapPackInstalled(): Boolean

/** Read GeoJSON text for an appellation geo asset file name (e.g. "123.geojson"). */
expect suspend fun readAppellationGeoJson(geoAsset: String): String?
