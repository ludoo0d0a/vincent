package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/** Downloads the optional French appellations GeoJSON map pack from the Worker. Returns bytes saved. */
@Composable
expect fun rememberMapPackDownload(onLoading: (Boolean) -> Unit, onResult: (Long?) -> Unit): () -> Unit

/** Whether the map pack is present on device. */
expect fun isMapPackInstalled(): Boolean

/** Read GeoJSON text for an appellation geo asset file name (e.g. "123.geojson"). */
expect suspend fun readAppellationGeoJson(geoAsset: String): String?
