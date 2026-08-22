package fr.geoking.vincent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import vincent.composeapp.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
suspend fun loadBundledOriginCentroids(): Unit = withContext(Dispatchers.Default) {
    runCatching {
        val bytes = Res.readBytes("files/origin-centroids.json")
        OriginGeocoder.loadCentroids(bytes.decodeToString())
    }
}

@OptIn(ExperimentalResourceApi::class)
suspend fun loadBundledMacroRegionsGeoJson(): String? = withContext(Dispatchers.Default) {
    runCatching {
        Res.readBytes("files/france-macro-regions.geojson").decodeToString()
    }.getOrNull()
}
