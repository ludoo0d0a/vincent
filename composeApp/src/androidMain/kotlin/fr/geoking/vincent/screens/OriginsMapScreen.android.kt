package fr.geoking.vincent.screens

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import fr.geoking.vincent.data.Appellations
import fr.geoking.vincent.data.CellarOrigins
import fr.geoking.vincent.data.OriginAggregate
import fr.geoking.vincent.data.OriginKind
import fr.geoking.vincent.data.loadBundledMacroRegionsGeoJson
import fr.geoking.vincent.data.loadBundledOriginCentroids
import fr.geoking.vincent.data.readAppellationGeoJson
import fr.geoking.vincent.data.isMapPackInstalled
import fr.geoking.vincent.model.Appellation
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.RecentRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun OriginsMapScreen(
    onBack: () -> Unit,
    initialTab: OriginsMapTab,
    highlightOriginKey: String?,
    onOpenBottle: (Bottle) -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(initialTab) }
    var assetsReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadBundledOriginCentroids()
        assetsReady = true
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        DataScreenHeader(
            title = stringResource(Res.string.origins_map_title),
            subtitle = when (tab) {
                OriginsMapTab.Cellar -> stringResource(
                    Res.string.origins_map_cellar_subtitle,
                    CellarOrigins.distinctOriginCount(),
                )
                OriginsMapTab.Reference -> stringResource(
                    Res.string.origins_map_subtitle,
                    Appellations.all.size,
                )
            },
            onBack = onBack,
        )

        TabRow(selectedTabIndex = tab.ordinal, containerColor = VincentColors.Surface) {
            Tab(
                selected = tab == OriginsMapTab.Cellar,
                onClick = { tab = OriginsMapTab.Cellar },
                text = { Text(stringResource(Res.string.origins_map_tab_cellar)) },
            )
            Tab(
                selected = tab == OriginsMapTab.Reference,
                onClick = { tab = OriginsMapTab.Reference },
                text = { Text(stringResource(Res.string.origins_map_tab_reference)) },
            )
        }

        if (!assetsReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.origins_map_loading), color = VincentColors.Muted, fontSize = 13.sp)
            }
            return@Column
        }

        when (tab) {
            OriginsMapTab.Cellar -> CellarOriginsTab(
                context = context,
                highlightOriginKey = highlightOriginKey,
                onOpenBottle = onOpenBottle,
            )
            OriginsMapTab.Reference -> ReferenceOriginsTab(context = context)
        }
    }
}

private data class ChoroplethOverlay(
    val agg: OriginAggregate,
    val points: List<GeoPoint>,
)

private data class PinOverlay(
    val agg: OriginAggregate,
    val point: GeoPoint,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellarOriginsTab(
    context: Context,
    highlightOriginKey: String?,
    onOpenBottle: (Bottle) -> Unit,
) {
    var colorFilter by remember { mutableStateOf<WineColor?>(null) }
    var selectedOriginKey by remember { mutableStateOf(highlightOriginKey) }
    var sheetBottles by remember { mutableStateOf<List<Bottle>?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mapPackReady = remember { isMapPackInstalled(context) }
    val bottleCount = fr.geoking.vincent.data.Cellar.bottles.size
    val aggregates = remember(bottleCount, colorFilter) { CellarOrigins.aggregate(colorFilter) }
    var macroGeoJson by remember { mutableStateOf<String?>(null) }
    var choropleths by remember { mutableStateOf<List<ChoroplethOverlay>>(emptyList()) }
    var pins by remember { mutableStateOf<List<PinOverlay>>(emptyList()) }

    LaunchedEffect(Unit) {
        macroGeoJson = loadBundledMacroRegionsGeoJson()
    }

    LaunchedEffect(aggregates, macroGeoJson, mapPackReady) {
        choropleths = aggregates
            .filter { it.origin.kind == OriginKind.Appellation || it.origin.kind == OriginKind.MacroRegion }
            .mapNotNull { agg ->
                val points = loadChoroplethPoints(context, agg, macroGeoJson, mapPackReady)
                if (points.isEmpty()) null else ChoroplethOverlay(agg, points)
            }
        pins = aggregates
            .filter { it.origin.kind == OriginKind.Country || it.origin.kind == OriginKind.Unmapped }
            .mapNotNull { agg ->
                val latLon = agg.origin.latLon ?: return@mapNotNull null
                PinOverlay(agg, GeoPoint(latLon.first, latLon.second))
            }
    }

    LaunchedEffect(highlightOriginKey) {
        if (highlightOriginKey != null) selectedOriginKey = highlightOriginKey
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (!mapPackReady) {
            Text(
                stringResource(Res.string.origins_map_pack_hint),
                fontSize = 11.sp,
                color = VincentColors.Muted,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorFilterChip(
                label = stringResource(Res.string.origins_map_filter_all),
                selected = colorFilter == null,
                onClick = { colorFilter = null },
            )
            WineColor.entries.forEach { color ->
                ColorFilterChip(
                    label = stringResource(color.label),
                    selected = colorFilter == color,
                    onClick = { colorFilter = if (colorFilter == color) null else color },
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(5.5)
                    controller.setCenter(GeoPoint(46.6, 2.2))
                }
            },
            update = { map ->
                map.overlays.clear()
                val maxCount = aggregates.maxOfOrNull { it.bottleCount }?.coerceAtLeast(1) ?: 1
                val allPoints = mutableListOf<GeoPoint>()

                choropleths.forEach { overlay ->
                    val agg = overlay.agg
                    val points = overlay.points
                    allPoints.addAll(points)
                    val alpha = (0x33 + (agg.bottleCount.toFloat() / maxCount * 0x99).toInt()).coerceIn(0x33, 0xCC)
                    val polygon = Polygon(map).apply {
                        this.points = points
                        fillPaint.color = (alpha shl 24) or 0xA04040
                        outlinePaint.color = if (agg.origin.key == selectedOriginKey) 0xFF6B1515.toInt() else 0xFFA04040.toInt()
                        outlinePaint.strokeWidth = if (agg.origin.key == selectedOriginKey) 5f else 2f
                        title = "${agg.origin.label} (${agg.bottleCount})"
                        setOnClickListener { _, _, _ ->
                            selectedOriginKey = agg.origin.key
                            sheetBottles = agg.bottles
                            true
                        }
                    }
                    map.overlays.add(polygon)
                }

                pins.forEach { overlay ->
                    val agg = overlay.agg
                    val point = overlay.point
                    allPoints.add(point)
                    val marker = Marker(map).apply {
                        position = point
                        title = "${agg.origin.label} (${agg.bottleCount})"
                        icon = countMarkerDrawable(context, agg.bottleCount, agg.origin.key == selectedOriginKey)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ ->
                            selectedOriginKey = agg.origin.key
                            sheetBottles = agg.bottles
                            true
                        }
                    }
                    map.overlays.add(marker)
                }

                when {
                    selectedOriginKey != null -> {
                        val highlight = choropleths.firstOrNull { it.agg.origin.key == selectedOriginKey }
                            ?: pins.firstOrNull { it.agg.origin.key == selectedOriginKey }
                        when (highlight) {
                            is ChoroplethOverlay -> {
                                val pts = highlight.points
                                val lats = pts.map { it.latitude }
                                val lons = pts.map { it.longitude }
                                map.zoomToBoundingBox(
                                    BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()),
                                    true,
                                    80,
                                )
                            }
                            is PinOverlay -> map.controller.animateTo(highlight.point, 6.0, 400L)
                            null -> Unit
                        }
                    }
                    allPoints.isNotEmpty() -> {
                        val lats = allPoints.map { it.latitude }
                        val lons = allPoints.map { it.longitude }
                        map.zoomToBoundingBox(
                            BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()),
                            true,
                            64,
                        )
                    }
                }

                map.invalidate()
            },
        )

        val unmapped = aggregates.filter { it.origin.kind == OriginKind.Unmapped && it.origin.latLon == null }
        if (unmapped.isNotEmpty()) {
            Text(
                stringResource(Res.string.origins_map_unmapped),
                fontSize = 11.sp,
                color = VincentColors.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            unmapped.take(3).forEach { agg ->
                Text(
                    "${agg.origin.label} (${agg.bottleCount})",
                    fontSize = 12.sp,
                    color = VincentColors.Fg,
                    modifier = Modifier
                        .clickable { sheetBottles = agg.bottles }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }

    if (sheetBottles != null) {
        ModalBottomSheet(onDismissRequest = { sheetBottles = null }, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(Res.string.origins_map_bottles_title, sheetBottles!!.size),
                    fontSize = 16.sp,
                    color = VincentColors.Fg,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                sheetBottles!!.forEach { bottle ->
                    RecentRow(bottle, onOpenBottle = {
                        sheetBottles = null
                        onOpenBottle(bottle)
                    })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReferenceOriginsTab(context: Context) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Appellation?>(null) }
    val filtered = remember(query, Appellations.all.size) {
        Appellations.search(query, limit = 200)
    }
    var geoPoints by remember { mutableStateOf<List<GeoPoint>?>(null) }

    LaunchedEffect(selected) {
        geoPoints = null
        val app = selected ?: return@LaunchedEffect
        if (app.geoAsset.isBlank()) return@LaunchedEffect
        geoPoints = withContext(Dispatchers.IO) {
            readAppellationGeoJson(context, app.geoAsset)?.let { GeoJsonParser.parsePoints(it) }
        }
    }

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
                    if (app.sign.isNotBlank()) append(" ù ${app.sign}")
                    if (app.department.isNotBlank()) append(" ù ${app.department}")
                },
                fontSize = 13.sp,
                color = VincentColors.Fg,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        ReferenceMapPanel(context = context, points = geoPoints, mapPackReady = isMapPackInstalled(context))

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
                    val meta = listOf(app.sign, app.category, app.department).filter { it.isNotBlank() }.joinToString(" ù ")
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

@Composable
private fun ReferenceMapPanel(
    context: Context,
    points: List<GeoPoint>?,
    mapPackReady: Boolean,
) {
    when {
        !mapPackReady -> Text(
            stringResource(Res.string.origins_map_pack_missing),
            fontSize = 11.sp,
            color = VincentColors.Muted,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        points == null -> Text(
            stringResource(Res.string.origins_map_select_appellation),
            fontSize = 11.sp,
            color = VincentColors.Muted,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        points.isEmpty() -> Text(
            stringResource(Res.string.origins_map_no_geometry),
            fontSize = 11.sp,
            color = VincentColors.Muted,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        else -> AndroidView(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                }
            },
            update = { map ->
                map.overlays.clear()
                val polygon = Polygon(map).apply {
                    this.points = points
                    fillPaint.color = 0x33A04040
                    outlinePaint.color = 0xFFA04040.toInt()
                    outlinePaint.strokeWidth = 3f
                }
                map.overlays.add(polygon)
                val lats = points.map { it.latitude }
                val lons = points.map { it.longitude }
                map.zoomToBoundingBox(
                    BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()),
                    true,
                    48,
                )
                map.invalidate()
            },
        )
    }
}

@Composable
private fun ColorFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) VincentColors.AccentSoft else VincentColors.Surface2
    val fg = if (selected) VincentColors.Accent else VincentColors.Muted
    Text(
        label,
        fontSize = 12.sp,
        color = fg,
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private suspend fun loadChoroplethPoints(
    context: Context,
    agg: OriginAggregate,
    macroGeoJson: String?,
    mapPackReady: Boolean,
): List<GeoPoint> {
    val origin = agg.origin
    if (origin.kind == OriginKind.Appellation && mapPackReady && !origin.geoAsset.isNullOrBlank()) {
        val json = withContext(Dispatchers.IO) { readAppellationGeoJson(context, origin.geoAsset!!) }
        if (json != null) return GeoJsonParser.parsePoints(json)
    }
    if (origin.macroRegionKey != null && macroGeoJson != null) {
        val feature = GeoJsonParser.parseFeatures(macroGeoJson)
            .firstOrNull { it.key == origin.macroRegionKey }
        if (feature != null) return feature.points
    }
    return origin.latLon?.let { (lat, lon) -> listOf(GeoPoint(lat, lon)) } ?: emptyList()
}

private fun countMarkerDrawable(context: Context, count: Int, highlighted: Boolean): ShapeDrawable {
    val size = (36 * context.resources.displayMetrics.density).toInt()
    return ShapeDrawable(OvalShape()).apply {
        intrinsicWidth = size
        intrinsicHeight = size
        paint.color = if (highlighted) 0xFF6B1515.toInt() else 0xFFA04040.toInt()
        paint.style = Paint.Style.FILL
    }.also {
        // Marker title shows the count; osmdroid draws icon only.
    }
}
