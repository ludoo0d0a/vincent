package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackMode
import fr.geoking.vincent.model.SampleData
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.ScreenHeader
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle

/** A range filter applied to the rack (dims non-matching cells). */
private data class RackFilter(val label: String, val test: (RackCell) -> Boolean)

private fun yearOf(cell: RackCell): Int? {
    val digits = cell.vintage?.filter { it.isDigit() }
    if (digits.isNullOrEmpty()) return null
    val n = digits.toInt()
    return if (n < 100) 2000 + n else n
}

private val rackFilters = listOf(
    RackFilter("≤ 2015") { (yearOf(it) ?: Int.MAX_VALUE) <= 2015 },
    RackFilter("2016–19") { yearOf(it)?.let { y -> y in 2016..2019 } == true },
    RackFilter("2020 +") { (yearOf(it) ?: 0) >= 2020 },
    RackFilter("≤ 25 €") { (it.price ?: Int.MAX_VALUE) <= 25 },
    RackFilter("25–50 €") { it.price?.let { p -> p in 26..50 } == true },
    RackFilter("> 50 €") { (it.price ?: 0) > 50 },
)

// Cell border ALWAYS encodes the wine colour (whatever the mode/filter).
private fun wineBorderColor(cell: RackCell): Color = cell.color?.glass ?: VincentColors.Border

// Interior fill encodes the active mode's bucket: wine colour, price group,
// vintage range or category — so the rack is readable at a glance per mode.
private fun priceHue(p: Int?): Color = when {
    p == null -> VincentColors.Muted
    p <= 25 -> Color(0xFF4CAF82)
    p <= 50 -> Color(0xFFE0A33A)
    else -> Color(0xFFC65454)
}

private fun vintageHue(y: Int?): Color = when {
    y == null -> VincentColors.Muted
    y <= 2015 -> Color(0xFF6B4FA0)
    y <= 2019 -> Color(0xFF4F86C6)
    else -> Color(0xFF4CA6A6)
}

private val categoryPalette = listOf(
    Color(0xFFB5462F), Color(0xFF8E5BB5), Color(0xFF4F86C6),
    Color(0xFFCB8A3A), Color(0xFF4CA67E), Color(0xFF9AA64C),
)

private fun interiorBase(cell: RackCell, mode: RackMode): Color = when (mode) {
    RackMode.COLOR -> cell.color?.glass ?: VincentColors.Accent
    RackMode.PRICE -> priceHue(cell.price)
    RackMode.VINTAGE -> vintageHue(yearOf(cell))
    RackMode.CATEGORY -> cell.category?.let { categoryPalette[it.ordinal % categoryPalette.size] } ?: VincentColors.Muted
}

@Composable
fun CellarScreen(
    modifier: Modifier = Modifier,
    onOpenBottle: (Bottle) -> Unit,
) {
    var mode by remember { mutableStateOf(RackMode.VINTAGE) }
    var filterIdx by remember { mutableIntStateOf(-1) }
    val filter = rackFilters.getOrNull(filterIdx)
    val occupied = SampleData.rackA.count { it.occupied }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader("Casiers", "${SampleData.rackA.size} emplacements · $occupied occupés")

        Column(Modifier.padding(horizontal = 16.dp)) {
            CellarTabs()
            Spacer(Modifier.height(10.dp))
            ModeSelector(mode) { mode = it }
            Spacer(Modifier.height(10.dp))
            FilterChips(filterIdx) { filterIdx = if (filterIdx == it) -1 else it }
            Spacer(Modifier.height(11.dp))
            RackGrid(mode, filter)
            Spacer(Modifier.height(11.dp))
            PeekCard(onOpenBottle)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CellarTabs() {
    val tabs = listOf("Cave A", "Cave B", "Réfrigérée")
    var selected by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) VincentColors.Accent else VincentColors.Surface)
                    .border(1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(9.dp))
                    .clickable { selected = i }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(t, fontSize = 12.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: RackMode, onMode: (RackMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RackMode.entries.forEach { m ->
            val on = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) VincentColors.Surface else Color.Transparent)
                    .clickable { onMode(m) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W700,
                    color = if (on) VincentColors.Accent else VincentColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun FilterChips(selectedIdx: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        rackFilters.forEachIndexed { i, f ->
            val on = i == selectedIdx
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) VincentColors.Fg else VincentColors.Surface)
                    .border(1.dp, if (on) VincentColors.Fg else VincentColors.Border, RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Text(f.label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
            }
        }
    }
}

@Composable
private fun RackGrid(mode: RackMode, filter: RackFilter?) {
    val matching = SampleData.rackA.count { it.occupied && (filter?.test?.invoke(it) ?: true) }
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SampleData.rackA.chunked(6).forEach { rowCells ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        rowCells.first().row,
                        fontSize = 10.sp,
                        color = VincentColors.Faint,
                        modifier = Modifier.width(14.dp),
                    )
                    rowCells.forEach { cell -> Cell(cell, mode, filter, Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (filter == null) "Touchez un mode ou un filtre" else "$matching bouteille${if (matching > 1) "s" else ""} dans ce filtre",
                    fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W500,
                )
                Text("73%", style = MonoNumber, color = VincentColors.Muted)
            }
        }
    }
}

@Composable
private fun Cell(cell: RackCell, mode: RackMode, filter: RackFilter?, modifier: Modifier = Modifier) {
    if (!cell.occupied) {
        Box(
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(VincentColors.Surface2)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(7.dp)),
        )
        return
    }
    val matches = filter?.test?.invoke(cell) ?: true
    // Interior follows the active mode; border ALWAYS keeps the wine colour, thick.
    val tint = lerp(Color.White, interiorBase(cell, mode), 0.22f)
    val wineBorder = wineBorderColor(cell)
    val label = when (mode) {
        RackMode.COLOR -> ""
        RackMode.PRICE -> "${cell.price}€"
        RackMode.VINTAGE -> cell.vintage.orEmpty()
        RackMode.CATEGORY -> cell.category?.short.orEmpty()
    }
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (matches) 1f else 0.32f)
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .border(if (cell.selected) 4.dp else 3.dp, wineBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.88f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    label,
                    style = MonoNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W800,
                    color = Color(0xFF2A1717),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PeekCard(onOpenBottle: (Bottle) -> Unit) {
    val bottle = Cellar.bottles.first { it.cellarSpot == "B3" || it.id == "leoville-2016" }
    VCard(Modifier.fillMaxWidth().clickable { onOpenBottle(bottle) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WineBottle(WineColor.RED, Modifier.size(width = 30.dp, height = 46.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Emplacement B3", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                Text("${bottle.domain} ${bottle.vintage} · ${bottle.price} €", fontSize = 11.sp, color = VincentColors.Muted)
            }
            ColorTag(bottle.color, label = bottle.category.label)
        }
    }
}
