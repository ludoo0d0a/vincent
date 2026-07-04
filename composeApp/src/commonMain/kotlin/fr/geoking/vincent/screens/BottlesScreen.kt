package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.matchingBottle
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.BottleThumb
import fr.geoking.vincent.ui.RackGridView
import fr.geoking.vincent.ui.RackSelectorTabs
import fr.geoking.vincent.ui.ScreenHeader
import fr.geoking.vincent.ui.Stars

private data class Filter(val label: String, val color: WineColor?, val favOnly: Boolean = false)

/** How the bottle results are laid out. */
private enum class BottleViewMode { GRID, COMPACT, CELLAR }

private sealed interface BottleImportStatus {
    data class Success(val count: Int, val source: String) : BottleImportStatus
    data object None : BottleImportStatus
    data object WrongType : BottleImportStatus
}

/** A faceted search dimension: its label, the full option list, and how many to show inline. */
private enum class Crit(val label: StringResource, val all: List<String>, val common: Int) {
    TASTE(
        Res.string.search_crit_taste,
        listOf("Fruité", "Sec", "Minéral", "Tannique", "Boisé", "Vif", "Rond", "Épicé",
            "Floral", "Léger", "Puissant", "Acidulé", "Doux", "Complexe", "Fumé", "Long en bouche"),
        6,
    ),
    GRAPE(
        Res.string.search_crit_grape,
        listOf("Cabernet Sauvignon", "Merlot", "Pinot Noir", "Syrah", "Grenache", "Chardonnay",
            "Sauvignon Blanc", "Chenin", "Riesling", "Gamay", "Cinsault", "Mourvèdre", "Viognier", "Sémillon"),
        5,
    ),
    REGION(
        Res.string.search_crit_region,
        listOf("Bordeaux", "Bourgogne", "Rhône", "Provence", "Loire", "Champagne",
            "Languedoc", "Alsace", "Beaujolais", "Sud-Ouest", "Italie", "Espagne", "Portugal"),
        6,
    ),
    MERCHANT(
        Res.string.search_crit_merchant,
        listOf("Lavinia", "Nicolas", "Caviste local", "En ligne", "Producteur", "Foire aux vins"),
        4,
    ),
    OCCASION(
        Res.string.search_crit_occasion,
        listOf("À offrir", "Cave de garde", "Tous les jours", "Fête", "Accord mets", "Découverte"),
        4,
    ),
}

/** All advanced (non quick-chip) filter criteria applied on top of the keyword + colour filters. */
private data class AdvFilters(
    val price: ClosedFloatingPointRange<Float>? = null,
    val selections: Map<Crit, Set<String>> = emptyMap(),
) {
    val activeCount: Int
        get() = selections.values.sumOf { it.size } + (if (price != null) 1 else 0)
}

private fun Bottle.matchesAdv(adv: AdvFilters, categoryLabels: Map<WineCategory, String>): Boolean {
    adv.price?.let { if (price < it.start || price > it.endInclusive) return false }

    val regions = adv.selections[Crit.REGION].orEmpty()
    if (regions.isNotEmpty() && !regions.any {
            it.lowercase() == provenance.lowercase() || it.lowercase() == categoryLabels[category]
        }) return false

    val grapes = adv.selections[Crit.GRAPE].orEmpty()
    if (grapes.isNotEmpty() && !grapes.any {
            appellation.lowercase().contains(it.lowercase()) || domain.lowercase().contains(it.lowercase())
        }) return false

    val tastes = adv.selections[Crit.TASTE].orEmpty()
    if (tastes.isNotEmpty() && !tastes.any { tastingNotes.lowercase().contains(it.lowercase()) }) return false

    val merchants = adv.selections[Crit.MERCHANT].orEmpty()
    if (merchants.isNotEmpty() && !merchants.any { merchant.lowercase().contains(it.lowercase()) }) return false

    val occasions = adv.selections[Crit.OCCASION].orEmpty()
    if (occasions.isNotEmpty() && !occasions.any { occasion.lowercase().contains(it.lowercase()) }) return false

    return true
}

@Composable
fun BottlesScreen(
    modifier: Modifier = Modifier,
    onOpenBottle: (Bottle) -> Unit,
    initialFavoritesOnly: Boolean = false,
    onOpenDataManagement: () -> Unit = {},
    onFiltersVisible: (Boolean) -> Unit = {},
) {
    val filterItems = listOf(
        Filter(stringResource(Res.string.bottles_filter_all), null),
        Filter(stringResource(WineColor.RED.label), WineColor.RED),
        Filter(stringResource(WineColor.WHITE.label), WineColor.WHITE),
        Filter(stringResource(WineColor.ROSE.label), WineColor.ROSE),
        Filter(stringResource(Res.string.bottles_filter_favorites), null, favOnly = true),
    )
    val favIndex = filterItems.indexOfFirst { it.favOnly }.coerceAtLeast(0)
    var selected by remember { mutableIntStateOf(if (initialFavoritesOnly) favIndex else 0) }
    var query by remember { mutableStateOf("") }
    var adv by remember { mutableStateOf(AdvFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    LaunchedEffect(showFilters) { onFiltersVisible(showFilters) }
    var viewMode by remember { mutableStateOf(BottleViewMode.GRID) }
    var rackIdx by remember { mutableIntStateOf(0) }

    val f = filterItems[selected]
    val categoryLabels = WineCategory.entries.associateWith { stringResource(it.label).lowercase() }

    // Bottles after the quick colour/favourite chip and the keyword field, but before the
    // advanced panel — also the base list the panel counts its live results against.
    val base = Cellar.bottles.filter {
        (f.color == null || it.color == f.color) && (!f.favOnly || it.favorite)
    }.filter { b ->
        val q = query.trim().lowercase()
        q.isBlank() ||
            b.domain.lowercase().contains(q) ||
            b.appellation.lowercase().contains(q) ||
            categoryLabels[b.category]?.contains(q) == true ||
            b.vintage.lowercase().contains(q)
    }.sortedByDescending { it.addedAt }
    val list = base.filter { it.matchesAdv(adv, categoryLabels) }

    val prices = Cellar.bottles.map { it.price }
    val priceMin = (prices.minOrNull() ?: 0).toFloat()
    val priceMaxRaw = (prices.maxOrNull() ?: 100).toFloat()
    val priceBounds = priceMin..(if (priceMaxRaw > priceMin) priceMaxRaw else priceMin + 1f)


    // Removable summary chips for every active advanced criterion.
    val activeChips: List<Pair<String, () -> Unit>> = buildList {
        adv.price?.let { range ->
            add("${range.start.toInt()}–${range.endInclusive.toInt()} €" to { adv = adv.copy(price = null) })
        }
        adv.selections.forEach { (crit, values) ->
            values.forEach { v ->
                add(v to {
                    adv = adv.copy(selections = adv.selections + (crit to (adv.selections[crit].orEmpty() - v)))
                })
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // --- Fixed top zone: header, search row, quick chips, active chips ---
            ScreenHeader(
                stringResource(Res.string.bottles_title),
                pluralStringResource(Res.plurals.bottles_subtitle_format, list.size, Cellar.totalBottles(), list.size),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (f.favOnly) VincentColors.AccentSoft else VincentColors.Surface2).border(1.dp, if (f.favOnly) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(12.dp)).clickable { selected = if (f.favOnly) 0 else favIndex },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Favorite, contentDescription = stringResource(Res.string.my_favorites), modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }

                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable { onOpenDataManagement() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.FileUpload, contentDescription = stringResource(Res.string.import_action), modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }
                    }
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) {
                        SearchField(
                            stringResource(Res.string.bottles_search_placeholder),
                            value = query,
                            onValueChange = { query = it },
                            onClear = { query = "" },
                        )
                    }
                    FilterButton(adv.activeCount) { showFilters = true }
                }
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) {
                        FilterChips(selected, filterItems) { selected = it }
                    }
                    ViewModeToggle(viewMode) { viewMode = it }
                }
                if (activeChips.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        activeChips.forEach { (label, onRemove) -> RemovableChip(label, onRemove) }
                    }
                }
                Spacer(Modifier.height(11.dp))
            }

            // --- Scrolling zone: only the results scroll ---
            val emptyMessage: @Composable () -> Unit = {
                Text(
                    if (query.isNotBlank()) stringResource(Res.string.bottles_no_result, query)
                    else stringResource(Res.string.bottles_no_result_filter),
                    fontSize = 13.sp, color = VincentColors.Muted, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            when (viewMode) {
                BottleViewMode.CELLAR -> CellarResults(
                    list = list,
                    searchActive = query.isNotBlank() || adv.activeCount > 0 || f.color != null || f.favOnly,
                    rackIdx = rackIdx,
                    onSelectRack = { rackIdx = it },
                    onOpenBottle = onOpenBottle,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(if (viewMode == BottleViewMode.COMPACT) 8.dp else 11.dp),
                ) {
                    if (viewMode == BottleViewMode.COMPACT) {
                        items(list) { b -> BottleRow(b) { onOpenBottle(b) } }
                    } else {
                        items(list.chunked(2)) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                pair.forEach { b -> BottleCard(b, Modifier.weight(1f)) { onOpenBottle(b) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        item { emptyMessage() }
                    }
                }
            }
        }

        if (showFilters) {
            AdvancedFilterPanel(
                initial = adv,
                priceBounds = priceBounds,
                baseList = base,
                categoryLabels = categoryLabels,
                onApply = { adv = it; showFilters = false },
                onClose = { showFilters = false },
            )
        }
    }
}

@Composable
fun SearchField(
    placeholder: String,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onClear: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(9.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = VincentColors.Fg),
            cursorBrush = SolidColor(VincentColors.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = VincentColors.Faint, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
        if (onClear != null && value.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.search_reset),
                tint = VincentColors.Faint,
                modifier = Modifier.size(16.dp).clickable { onClear() },
            )
        }
    }
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    val active = activeCount > 0
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (active) VincentColors.AccentSoft else VincentColors.Surface)
                .border(1.dp, if (active) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(13.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Tune, contentDescription = stringResource(Res.string.search_title), modifier = Modifier.size(18.dp), tint = VincentColors.Accent)
        }
        if (active) {
            Box(
                Modifier
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(17.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VincentColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("$activeCount", fontSize = 9.5.sp, fontWeight = FontWeight.W700, color = Color.White)
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VincentColors.AccentSoft)
            .border(1.dp, VincentColors.Accent, RoundedCornerShape(20.dp))
            .padding(start = 11.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent)
        Spacer(Modifier.size(4.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(Res.string.search_reset),
            tint = VincentColors.Accent,
            modifier = Modifier.size(13.dp).clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun FilterChips(selected: Int, filterItems: List<Filter>, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        filterItems.forEachIndexed { i, filter ->
            val on = i == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) VincentColors.Fg else VincentColors.Surface)
                    .border(1.dp, if (on) VincentColors.Fg else VincentColors.Border, RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (filter.color != null) {
                    Box(Modifier.padding(end = 5.dp).size(8.dp).clip(RoundedCornerShape(50)).background(filter.color.glass))
                }
                Text(filter.label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
            }
        }
    }
}

@Composable
fun BottleCard(b: Bottle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(lerp(Color.White, b.color.glass, 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            BottleThumb(b, Modifier.size(width = 30.dp, height = 78.dp))
            if (b.favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favori",
                    tint = VincentColors.Accent,
                    modifier = Modifier.align(Alignment.TopStart).size(15.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0D0D11))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("×${b.quantity}", style = MonoNumber, fontSize = 10.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(b.domain, fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        Text("${b.appellation} · ${b.vintage}", fontSize = 10.5.sp, color = VincentColors.Muted)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${b.price} €", style = MonoNumber, color = VincentColors.Fg)
            Stars(b.rating)
        }
    }
}

/** Compact single-row bottle entry: small thumbnail + key facts on one line. */
@Composable
private fun BottleRow(b: Bottle, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(lerp(Color.White, b.color.glass, 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            BottleThumb(b, Modifier.size(width = 20.dp, height = 44.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    b.domain,
                    fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (b.favorite) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                }
            }
            Text("${b.appellation} · ${b.vintage}", fontSize = 11.sp, color = VincentColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Stars(b.rating)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${b.price} €", style = MonoNumber, color = VincentColors.Fg)
            Text("×${b.quantity}", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
        }
    }
}

/**
 * Cellar layout of the results: reuses the rack grid, lighting up cells whose
 * bottle is part of the current search and dimming the rest. With several racks,
 * a tab row lets the user pick which cellar to inspect.
 */
@Composable
private fun CellarResults(
    list: List<Bottle>,
    searchActive: Boolean,
    rackIdx: Int,
    onSelectRack: (Int) -> Unit,
    onOpenBottle: (Bottle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val racks = Racks.all
    if (racks.isEmpty()) return
    val idx = rackIdx.coerceIn(0, racks.lastIndex)
    val rack = racks[idx]
    val highlight: (RackCell) -> Boolean = { cell ->
        if (!searchActive) cell.occupied else cell.matchingBottle(list) != null
    }
    val matchCount = rack.cells.count { highlight(it) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 90.dp),
    ) {
        if (racks.size > 1) {
            RackSelectorTabs(racks.map { it.name }, idx, onSelectRack, Modifier.fillMaxWidth())
            Spacer(Modifier.height(11.dp))
        }
        RackGridView(
            rack = rack,
            highlight = highlight,
            onCellClick = { _, cell -> cell.matchingBottle(Cellar.bottles)?.let(onOpenBottle) },
        )
        Spacer(Modifier.height(9.dp))
        Text(
            pluralStringResource(Res.plurals.cellar_results_matching, matchCount, matchCount, rack.name),
            fontSize = 12.sp, color = VincentColors.Muted, fontWeight = FontWeight.W500,
        )
    }
}

@Composable
private fun ViewModeToggle(mode: BottleViewMode, onSelect: (BottleViewMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ViewModeButton(Icons.Filled.GridView, Res.string.view_mode_grid, mode == BottleViewMode.GRID) { onSelect(BottleViewMode.GRID) }
        ViewModeButton(Icons.AutoMirrored.Filled.ViewList, Res.string.view_mode_compact, mode == BottleViewMode.COMPACT) { onSelect(BottleViewMode.COMPACT) }
        ViewModeButton(Icons.Filled.GridOn, Res.string.view_mode_cellar, mode == BottleViewMode.CELLAR) { onSelect(BottleViewMode.CELLAR) }
    }
}

@Composable
private fun ViewModeButton(icon: ImageVector, desc: StringResource, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) VincentColors.Surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = stringResource(desc), modifier = Modifier.size(18.dp), tint = if (on) VincentColors.Accent else VincentColors.Muted)
    }
}

// --- Advanced filter slide-over panel (folds in the former SEARCH tab) ---

@Composable
private fun AdvancedFilterPanel(
    initial: AdvFilters,
    priceBounds: ClosedFloatingPointRange<Float>,
    baseList: List<Bottle>,
    categoryLabels: Map<WineCategory, String>,
    onApply: (AdvFilters) -> Unit,
    onClose: () -> Unit,
) {
    val predefinedRanges = listOf(
        0f..5f, 5f..10f, 10f..20f, 20f..50f, 50f..100f, 100f..maxOf(1000f, priceBounds.endInclusive)
    )

    var price by remember { mutableStateOf(initial.price ?: priceBounds) }
    var showExactPrice by remember {
        mutableStateOf(initial.price != null && predefinedRanges.none { it.start == initial.price.start && it.endInclusive == initial.price.endInclusive })
    }
    var selections by remember { mutableStateOf(initial.selections) }
    var picker by remember { mutableStateOf<Crit?>(null) }

    fun toggle(crit: Crit, item: String) {
        val current = selections[crit] ?: emptySet()
        selections = selections + (crit to (if (item in current) current - item else current + item))
    }

    val priceActive = price.start > priceBounds.start || price.endInclusive < priceBounds.endInclusive
    val pending = AdvFilters(if (priceActive) price else null, selections)
    val count = baseList.count { it.matchesAdv(pending, categoryLabels) }

    Box(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.search_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                    Text(pluralStringResource(Res.plurals.selected_count, pending.activeCount, pending.activeCount), fontSize = 11.5.sp, color = VincentColors.Muted)
                }
                Text(
                    stringResource(Res.string.search_reset),
                    fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent,
                    modifier = Modifier.clickable { price = priceBounds; selections = emptyMap() },
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                GroupLabel(stringResource(Res.string.search_price_label))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    predefinedRanges.forEach { range ->
                        val label = if (range.endInclusive > 500f) "> 100 €" else "${range.start.toInt()}–${range.endInclusive.toInt()} €"
                        val on = !showExactPrice && price.start == range.start && price.endInclusive == range.endInclusive
                        Chip(label, on) {
                            showExactPrice = false
                            price = range
                        }
                    }
                    MoreChip(on = showExactPrice) { showExactPrice = !showExactPrice }
                }

                if (showExactPrice) {
                    Spacer(Modifier.height(12.dp))
                    PriceRange(priceBounds, price) { price = it }
                }
                Spacer(Modifier.height(20.dp))

                Crit.entries.forEach { crit ->
                    CritSection(
                        crit = crit,
                        selected = selections[crit] ?: emptySet(),
                        onToggle = { toggle(crit, it) },
                        onMore = { picker = crit },
                    )
                    Spacer(Modifier.height(15.dp))
                }
                Spacer(Modifier.height(4.dp))
            }

            Button(
                onClick = { onApply(pending) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text(pluralStringResource(Res.plurals.see_bottles, count, count), fontWeight = FontWeight.W700)
            }
        }

        picker?.let { crit ->
            CriteriaPicker(
                crit = crit,
                selected = selections[crit] ?: emptySet(),
                onToggle = { toggle(crit, it) },
                onClose = { picker = null },
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Muted, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun CritSection(crit: Crit, selected: Set<String>, onToggle: (String) -> Unit, onMore: () -> Unit) {
    val common = crit.all.take(crit.common)
    // Always surface selected items even if they live beyond the common subset.
    val shown = (common + selected.filter { it !in common }).distinct()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GroupLabel(stringResource(crit.label))
        if (selected.isNotEmpty()) {
            Text(pluralStringResource(Res.plurals.selected_count, selected.size, selected.size), fontSize = 10.5.sp, color = VincentColors.Accent, fontWeight = FontWeight.W600)
        }
    }
    (shown + "…").chunked(3).forEach { row ->
        Row(Modifier.padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            row.forEach { item ->
                if (item == "…") MoreChip(on = false, onClick = onMore) else Chip(item, item in selected) { onToggle(item) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (on) VincentColors.Fg else VincentColors.Surface)
            .border(1.dp, if (on) VincentColors.Fg else VincentColors.Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
    }
}

@Composable
private fun MoreChip(on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (on) VincentColors.Fg else VincentColors.AccentSoft)
            .border(1.dp, if (on) VincentColors.Fg else VincentColors.Accent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text("…", fontSize = 13.sp, fontWeight = FontWeight.W800, color = if (on) Color.White else VincentColors.Accent)
    }
}

@Composable
private fun CriteriaPicker(crit: Crit, selected: Set<String>, onToggle: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(stringResource(crit.label), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(pluralStringResource(Res.plurals.criteria_count, crit.all.size, crit.all.size) + " · " + pluralStringResource(Res.plurals.selected_count, selected.size, selected.size), fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            crit.all.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) { WideChip(item, item in selected) { onToggle(item) } }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
        ) { Text(stringResource(Res.string.search_apply), fontWeight = FontWeight.W700) }
    }
}

@Composable
private fun WideChip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (on) VincentColors.AccentSoft else VincentColors.Surface)
            .border(if (on) 1.5.dp else 1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = if (on) VincentColors.Accent else VincentColors.Fg)
        if (on) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
    }
}

@Composable
private fun PriceRange(
    bounds: ClosedFloatingPointRange<Float>,
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${value.start.toInt()} €", style = MonoNumber, color = VincentColors.Fg)
            Text("${value.endInclusive.toInt()} €", style = MonoNumber, color = VincentColors.Fg)
        }
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = bounds,
            colors = SliderDefaults.colors(
                thumbColor = VincentColors.Accent,
                activeTrackColor = VincentColors.Accent,
                inactiveTrackColor = VincentColors.Border,
            ),
        )
    }
}
