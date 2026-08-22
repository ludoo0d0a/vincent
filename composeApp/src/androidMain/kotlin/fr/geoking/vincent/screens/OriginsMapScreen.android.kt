package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Appellations
import fr.geoking.vincent.model.Appellation
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataScreenHeader
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

@Composable
actual fun OriginsMapScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Appellation?>(null) }
    val filtered = remember(query, Appellations.all.size) {
        Appellations.search(query, limit = 200)
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        DataScreenHeader(
            title = stringResource(Res.string.origins_map_title),
            subtitle = stringResource(Res.string.origins_map_subtitle, Appellations.all.size),
            onBack = onBack,
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.origins_map_search)) },
                singleLine = true,
            )

            selected?.let { app ->
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append(app.name)
                        if (app.sign.isNotBlank()) append("  ${app.sign}")
                        if (app.department.isNotBlank()) append("  ${app.department}")
                    },
                    fontSize = 13.sp,
                    color = VincentColors.Fg,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            OriginsMapPanel(selected)

            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { app ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = app }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(app.name, fontSize = 14.sp, color = VincentColors.Fg)
                        val meta = listOf(app.sign, app.category, app.department).filter { it.isNotBlank() }.joinToString("  ")
                        if (meta.isNotEmpty()) {
                            Text(meta, fontSize = 11.sp, color = VincentColors.Muted)
                        }
                    }
                }
            }

            Text(
                stringResource(Res.string.appellations_attribution),
                fontSize = 10.sp,
                color = VincentColors.Muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
fun OriginsMapPanel(appellation: Appellation?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapReady = remember { fr.geoking.vincent.data.isMapPackInstalled(context) }

    if (!mapReady) {
        Text(
            stringResource(Res.string.origins_map_pack_missing),
            fontSize = 11.sp,
            color = VincentColors.Muted,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        return
    }

    if (appellation == null || appellation.geoAsset.isBlank()) {
        Text(
            stringResource(Res.string.origins_map_select_appellation),
            fontSize = 11.sp,
            color = VincentColors.Muted,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        return
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(10.0)
            }
        },
        update = { map ->
            map.overlays.clear()
            val geoJson = kotlinx.coroutines.runBlocking {
                fr.geoking.vincent.data.readAppellationGeoJson(context, appellation.geoAsset)
            } ?: return@AndroidView
            val points = parseGeoJsonPoints(geoJson)
            if (points.isEmpty()) return@AndroidView
            val polygon = org.osmdroid.views.overlay.Polygon(map).apply {
                this.points = points
                fillPaint.color = 0x33A04040
                outlinePaint.color = 0xFFA04040.toInt()
                outlinePaint.strokeWidth = 3f
            }
            map.overlays.add(polygon)
            val lats = points.map { it.latitude }
            val lons = points.map { it.longitude }
            map.zoomToBoundingBox(
                org.osmdroid.util.BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()),
                true,
                48,
            )
            map.invalidate()
        },
    )
}

private fun parseGeoJsonPoints(json: String): List<org.osmdroid.util.GeoPoint> {
    val root = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return emptyList()
    val coords = when (root.optString("type")) {
        "Polygon" -> root.optJSONArray("coordinates")?.optJSONArray(0)
        "Feature" -> {
            val geom = root.optJSONObject("geometry") ?: return emptyList()
            if (geom.optString("type") != "Polygon") return emptyList()
            geom.optJSONArray("coordinates")?.optJSONArray(0)
        }
        "FeatureCollection" -> {
            val feature = root.optJSONArray("features")?.optJSONObject(0) ?: return emptyList()
            val geom = feature.optJSONObject("geometry") ?: return emptyList()
            geom.optJSONArray("coordinates")?.optJSONArray(0)
        }
        else -> null
    } ?: return emptyList()
    return coords.toGeoPoints()
}

private fun org.json.JSONArray.toGeoPoints(): List<org.osmdroid.util.GeoPoint> = buildList {
    for (i in 0 until length()) {
        val pair = optJSONArray(i) ?: continue
        if (pair.length() < 2) continue
        add(org.osmdroid.util.GeoPoint(pair.getDouble(1), pair.getDouble(0)))
    }
}
