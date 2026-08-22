package fr.geoking.vincent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import vincent.composeapp.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
suspend fun loadBundledPopularGrapes(): List<fr.geoking.vincent.model.Grape> = withContext(Dispatchers.Default) {
    runCatching {
        val bytes = Res.readBytes("files/grapes-popular.json")
        ReferenceDataImport.parseGrapesJson(bytes.decodeToString())
    }.getOrElse { emptyList() }
}
