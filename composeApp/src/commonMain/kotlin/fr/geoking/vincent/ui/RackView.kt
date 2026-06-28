package fr.geoking.vincent.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackFormat
import fr.geoking.vincent.model.RackMode
import fr.geoking.vincent.model.cellSpotLabel
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

/** Whether [rowIndex] is shifted right for the quinconce, honouring [Rack.staggerOffset]. */
internal fun Rack.rowShifted(rowIndex: Int): Boolean =
    staggered && rowIndex % 2 == (if (staggerOffset) 0 else 1)

/**
 * Lays out a [RackFormat.X] rack as [Rack.squareRows]×[Rack.squareCols] X-bins. Each square
 * groups the matching 2×2 block of cells, drawn with a diagonal cross, the four [cellSlot]s
 * placed en quinconce (top / right / bottom / left). [cellSlot] receives the flat cell index,
 * the cell, and a ready-positioned modifier it must apply (callers reuse their own cell UI).
 */
@Composable
fun XBinGrid(
    rack: Rack,
    modifier: Modifier = Modifier,
    cellSlot: @Composable (gi: Int, cell: RackCell, modifier: Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sr in 0 until rack.squareRows) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${rowLabel(2 * sr)}${rowLabel(2 * sr + 1)}",
                    fontSize = 10.sp,
                    color = VincentColors.Faint,
                    modifier = Modifier.width(16.dp),
                )
                for (sc in 0 until rack.squareCols) {
                    XBinSquare(rack, sr, sc, Modifier.weight(1f), cellSlot)
                }
            }
        }
    }
}

@Composable
private fun XBinSquare(
    rack: Rack,
    squareRow: Int,
    squareCol: Int,
    modifier: Modifier = Modifier,
    cellSlot: @Composable (gi: Int, cell: RackCell, modifier: Modifier) -> Unit,
) {
    val topRow = squareRow * 2
    val bottomRow = squareRow * 2 + 1
    val leftCol = squareCol * 2
    val rightCol = squareCol * 2 + 1
    fun gi(row: Int, col: Int) = row * rack.cols + col

    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = 1.4.dp.toPx())
            drawRect(color = VincentColors.Border, style = stroke)
            drawLine(VincentColors.Border, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = stroke.width)
            drawLine(VincentColors.Border, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = stroke.width)
        }
        // Four bottles arranged en quinconce inside the X.
        val frac = 0.42f
        @Composable
        fun slot(gi: Int, align: Alignment) =
            cellSlot(gi, rack.cells[gi], Modifier.align(align).fillMaxWidth(frac))
        slot(gi(topRow, leftCol), Alignment.TopCenter)
        slot(gi(topRow, rightCol), Alignment.CenterEnd)
        slot(gi(bottomRow, rightCol), Alignment.BottomCenter)
        slot(gi(bottomRow, leftCol), Alignment.CenterStart)
    }
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
            if (rack.format == RackFormat.X) {
                XBinGrid(rack) { gi, cell, m ->
                    ReadOnlyCell(
                        cell = cell,
                        mode = mode,
                        highlighted = highlight(cell),
                        onClick = { onCellClick(gi, cell) },
                        modifier = m,
                    )
                }
                return@Column
            }
            rack.cells.chunked(rack.cols).forEachIndexed { rowIndex, rowCells ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        rowLabel(rowIndex),
                        fontSize = 10.sp,
                        color = VincentColors.Faint,
                        modifier = Modifier.width(14.dp),
                    )
                    val shiftRight = rack.rowShifted(rowIndex)
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

/**
 * Visual rack grid for placement or display — highlights a selected (empty)
 * cell or a current (occupied) cell.
 */
@Composable
fun PlacementRackGrid(
    rack: Rack,
    selectedCell: Int? = null,
    currentCell: Int? = null,
    showHint: Boolean = true,
    onPick: (Int) -> Unit = {},
) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (rack.format == RackFormat.X) {
                XBinGrid(rack) { gi, cell, m ->
                    val highlight = when {
                        gi == selectedCell && !cell.occupied -> PlacementCellHighlight.Selected
                        gi == currentCell && cell.occupied -> PlacementCellHighlight.Current
                        !cell.occupied -> PlacementCellHighlight.Available
                        else -> PlacementCellHighlight.Occupied
                    }
                    PlacementGridCell(
                        cell = cell,
                        spotLabel = cellSpotLabel(gi, rack.cols),
                        highlight = highlight,
                        onClick = { if (!cell.occupied) onPick(gi) },
                        modifier = m,
                    )
                }
            } else {
                rack.cells.chunked(rack.cols).forEachIndexed { rowIndex, rowCells ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            rowLabel(rowIndex),
                            fontSize = 10.sp,
                            color = VincentColors.Faint,
                            modifier = Modifier.width(14.dp),
                        )
                        val shiftRight = rack.rowShifted(rowIndex)
                        if (shiftRight) Spacer(Modifier.weight(0.5f))
                        rowCells.forEachIndexed { colIndex, cell ->
                            val gi = rowIndex * rack.cols + colIndex
                            val highlight = when {
                                gi == selectedCell && !cell.occupied -> PlacementCellHighlight.Selected
                                gi == currentCell && cell.occupied -> PlacementCellHighlight.Current
                                !cell.occupied -> PlacementCellHighlight.Available
                                else -> PlacementCellHighlight.Occupied
                            }
                            PlacementGridCell(
                                cell = cell,
                                spotLabel = cellSpotLabel(gi, rack.cols),
                                highlight = highlight,
                                onClick = { if (!cell.occupied) onPick(gi) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rack.staggered && !shiftRight) Spacer(Modifier.weight(0.5f))
                    }
                }
            }
            if (showHint) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(Res.string.add_placement_tap_free),
                    fontSize = 11.sp,
                    color = VincentColors.Muted,
                    fontWeight = FontWeight.W500,
                )
            }
        }
    }
}

@Composable
fun PlacementGridCell(
    cell: RackCell,
    spotLabel: String,
    highlight: PlacementCellHighlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (highlight) {
        PlacementCellHighlight.Available,
        PlacementCellHighlight.Selected,
        -> {
            val selected = highlight == PlacementCellHighlight.Selected
            Box(
                modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) VincentColors.Accent else VincentColors.Surface2)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) VincentColors.Accent else VincentColors.Border,
                        shape = RoundedCornerShape(7.dp),
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        spotLabel,
                        style = MonoNumber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W700,
                        color = VincentColors.Muted,
                    )
                }
            }
        }
        PlacementCellHighlight.Current -> {
            val wineBorder = cell.color?.glass ?: VincentColors.Accent
            val tint = lerp(Color.White, cell.color?.glass ?: VincentColors.Accent, 0.22f)
            Box(
                modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint)
                    .border(3.dp, wineBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(2.dp)
                        .border(2.dp, VincentColors.Accent, RoundedCornerShape(6.dp)),
                )
                Text(
                    stringResource(Res.string.add_placement_current),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.W800,
                    color = VincentColors.Accent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(VincentColors.AccentSoft)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        PlacementCellHighlight.Occupied -> {
            val wineBorder = cell.color?.glass ?: VincentColors.Border
            val tint = lerp(Color.White, cell.color?.glass ?: VincentColors.Accent, 0.22f)
            Box(
                modifier
                    .aspectRatio(1f)
                    .alpha(0.35f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint)
                    .border(2.dp, wineBorder, RoundedCornerShape(8.dp)),
            )
        }
    }
}

enum class PlacementCellHighlight {
    Occupied,
    Available,
    Selected,
    Current,
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
