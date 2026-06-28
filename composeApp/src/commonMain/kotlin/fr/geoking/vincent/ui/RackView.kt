package fr.geoking.vincent.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackMode
import fr.geoking.vincent.model.rowLabel
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors

// --- Shared rack colouring (single source of truth for both the interactive
// cellar grid and the read-only search view below). ---

/** Vintage parsed to a 4-digit year (e.g. "16" → 2016), or null when unknown. */
internal fun rackYearOf(cell: RackCell): Int? {
    val digits = cell.vintage?.filter { it.isDigit() }
    if (digits.isNullOrEmpty()) return null
    val n = digits.toInt()
    return if (n < 100) 2000 + n else n
}

// Cell border ALWAYS encodes the wine colour.
internal fun rackWineBorderColor(cell: RackCell): Color = cell.color?.glass ?: VincentColors.Border

internal fun rackPriceHue(p: Int?): Color = when {
    p == null -> VincentColors.Muted
    p <= 25 -> Color(0xFF4CAF82)
    p <= 50 -> Color(0xFFE0A33A)
    else -> Color(0xFFC65454)
}

internal fun rackVintageHue(y: Int?): Color = when {
    y == null -> VincentColors.Muted
    y <= 2015 -> Color(0xFF6B4FA0)
    y <= 2019 -> Color(0xFF4F86C6)
    else -> Color(0xFF4CA6A6)
}

internal val rackCategoryPalette = listOf(
    Color(0xFFB5462F), Color(0xFF8E5BB5), Color(0xFF4F86C6),
    Color(0xFFCB8A3A), Color(0xFF4CA67E), Color(0xFF9AA64C),
)

/** Interior fill for an occupied cell, encoding the active [mode]'s bucket. */
internal fun rackInteriorBase(cell: RackCell, mode: RackMode): Color = when (mode) {
    RackMode.COLOR -> cell.color?.glass ?: VincentColors.Accent
    RackMode.PRICE -> rackPriceHue(cell.price)
    RackMode.VINTAGE -> rackVintageHue(rackYearOf(cell))
    RackMode.CATEGORY -> cell.category?.let { rackCategoryPalette[it.ordinal % rackCategoryPalette.size] } ?: VincentColors.Muted
}

/**
 * Read-only rendering of a [Rack] grid, reusing the cellar's visual language
 * (staggered rows, wine-colour borders, mode-tinted interiors). Occupied cells
 * for which [highlight] returns false are dimmed, so the grid can double as a
 * search-results layout. Tapping an occupied cell invokes [onCellClick].
 */
@Composable
fun RackGridView(
    rack: Rack,
    modifier: Modifier = Modifier,
    mode: RackMode = RackMode.COLOR,
    highlight: (RackCell) -> Boolean = { true },
    onCellClick: (Int, RackCell) -> Unit = { _, _ -> },
) {
    VCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            rack.cells.chunked(rack.cols).forEachIndexed { rowIndex, rowCells ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        rowLabel(rowIndex),
                        fontSize = 10.sp,
                        color = VincentColors.Faint,
                        modifier = Modifier.width(14.dp),
                    )
                    val shiftRight = rack.staggered && rowIndex % 2 == 1
                    if (shiftRight) Spacer(Modifier.weight(0.5f))
                    rowCells.forEachIndexed { colIndex, cell ->
                        val gi = rowIndex * rack.cols + colIndex
                        ReadOnlyCell(
                            cell = cell,
                            mode = mode,
                            highlighted = highlight(cell),
                            onClick = { onCellClick(gi, cell) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rack.staggered && !shiftRight) Spacer(Modifier.weight(0.5f))
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyCell(
    cell: RackCell,
    mode: RackMode,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val tint = lerp(Color.White, rackInteriorBase(cell, mode), 0.22f)
    val label = when (mode) {
        RackMode.COLOR -> ""
        RackMode.PRICE -> "${cell.price}€"
        RackMode.VINTAGE -> cell.vintage.orEmpty()
        RackMode.CATEGORY -> cell.category?.short.orEmpty()
    }
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (highlighted) 1f else 0.28f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(tint)
            .border(
                if (highlighted) 3.dp else 2.dp,
                rackWineBorderColor(cell),
                RoundedCornerShape(8.dp),
            ),
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

/** Horizontally-scrollable pill tabs to pick one rack among several. */
@Composable
fun RackSelectorTabs(
    names: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        names.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) VincentColors.Accent else VincentColors.Surface)
                    .border(1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(9.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = if (on) Color.White else VincentColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
