package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.data.RackClipboard
import fr.geoking.vincent.data.RackClipboardEntry
import fr.geoking.vincent.data.RackClipboardMode
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackMode
import fr.geoking.vincent.model.RackPlacement
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.emptyRack
import fr.geoking.vincent.model.rowLabel
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
    onAddToCell: (RackPlacement) -> Unit = {},
) {
    var rackIdx by remember { mutableIntStateOf(0) }
    val rack = Racks.all[rackIdx.coerceIn(0, Racks.all.lastIndex)]
    var mode by remember { mutableStateOf(RackMode.VINTAGE) }
    var filterIdx by remember { mutableIntStateOf(-1) }
    val filter = rackFilters.getOrNull(filterIdx)
    var editing by remember { mutableStateOf(false) }
    var selectedIdx by remember(rackIdx) {
        mutableIntStateOf(
            rack.cells.indexOfFirst { it.selected }.takeIf { it >= 0 }
                ?: rack.cells.indexOfFirst { it.occupied }.coerceAtLeast(0),
        )
    }
    // Index of the bottle being moved (null when not in move mode).
    var moving by remember(rackIdx) { mutableStateOf<Int?>(null) }
    var dragFrom by remember(rackIdx) { mutableStateOf<Int?>(null) }
    var dragHover by remember(rackIdx) { mutableStateOf<Int?>(null) }
    var cellBounds by remember(rackIdx) { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    val clipboard = RackClipboard.entry

    fun findCellAt(offset: Offset): Int? =
        cellBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(offset) }?.key

    fun finishDrag(from: Int?, to: Int?) {
        if (from != null && to != null && from != to) {
            val target = rack.cells.getOrNull(to)
            if (target != null && !target.occupied) {
                Racks.moveBetween(rackIdx, from, rackIdx, to)
                selectedIdx = to
            }
        }
        dragFrom = null
        dragHover = null
    }

    fun pasteAt(cellIdx: Int) {
        val clip = RackClipboard.entry ?: return
        val target = rack.cells.getOrNull(cellIdx) ?: return
        if (target.occupied) return
        val cutSource = if (clip.mode == RackClipboardMode.CUT) clip.rackIndex to clip.cellIndex else null
        if (Racks.pasteCell(rackIdx, cellIdx, clip.cell, cutSource)) {
            selectedIdx = cellIdx
            if (clip.mode == RackClipboardMode.CUT) RackClipboard.clear()
        }
    }

    val onCellTap: (Int) -> Unit = { i ->
        val c = rack.cells.getOrNull(i)
        val mv = moving
        when {
            mv != null -> {
                if (c != null && !c.occupied) {
                    Racks.moveBetween(rackIdx, mv, rackIdx, i)
                    selectedIdx = i
                }
                moving = null
            }
            c != null && c.occupied -> selectedIdx = i
            clipboard != null -> {
                selectedIdx = i
                pasteAt(i)
            }
            else -> {
                selectedIdx = i
                onAddToCell(RackPlacement(rackIdx, i))
            }
        }
    }

    var status by remember { mutableStateOf<String?>(null) }
    val importCsv = rememberCsvImport { text ->
        val result = CsvFormat.parse(text)
        if (result.type == CsvFormat.ImportType.RACKS) {
            result.racks.forEach { Racks.add(it) }
            status = "Importé : ${result.racks.size} casier${if (result.racks.size > 1) "s" else ""}"
        } else {
            status = "Le fichier ne semble pas être un export de casiers."
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                "Casiers",
                "${rack.capacity} emplacements · ${rack.occupiedCount} occupés",
                trailing = {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable { importCsv() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.FileUpload, contentDescription = "Importer", modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }
                }
            )

            Column(Modifier.padding(horizontal = 16.dp)) {
                if (status != null) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                    ) { Text(status!!, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                }
                CellarTabs(
                    names = Racks.all.map { it.name },
                    selected = rackIdx,
                    onSelect = { rackIdx = it },
                    onEdit = { editing = true },
                    onAdd = {
                        Racks.add(emptyRack("Cave ${('A' + Racks.all.size)}", 4, 4, false))
                        rackIdx = Racks.all.lastIndex
                    },
                )
                Spacer(Modifier.height(10.dp))
                ModeSelector(mode) { mode = it }
                Spacer(Modifier.height(10.dp))
                FilterChips(filterIdx) { filterIdx = if (filterIdx == it) -1 else it }
                Spacer(Modifier.height(11.dp))
                RackGrid(
                    rack, rackIdx, mode, filter, selectedIdx, moving, dragFrom, dragHover, clipboard,
                    onCellTap,
                    onCellBounds = { idx, bounds ->
                        cellBounds = cellBounds.toMutableMap().also { it[idx] = bounds }
                    },
                    onDragStart = { idx ->
                        moving = null
                        dragFrom = idx
                        dragHover = null
                    },
                    onDragMove = { offset -> dragHover = findCellAt(offset) },
                    onDragEnd = { finishDrag(dragFrom, dragHover) },
                    onDragCancel = {
                        dragFrom = null
                        dragHover = null
                    },
                )
                Spacer(Modifier.height(11.dp))
                PeekCard(
                    rack, rackIdx, selectedIdx, moving == selectedIdx, clipboard, onOpenBottle,
                    onAddBottle = { onAddToCell(RackPlacement(rackIdx, selectedIdx)) },
                    onMove = { moving = if (moving == selectedIdx) null else selectedIdx },
                    onCut = {
                        val cell = rack.cells.getOrNull(selectedIdx) ?: return@PeekCard
                        if (cell.occupied) RackClipboard.cut(rackIdx, selectedIdx, cell)
                    },
                    onCopy = {
                        val cell = rack.cells.getOrNull(selectedIdx) ?: return@PeekCard
                        if (cell.occupied) RackClipboard.copy(rackIdx, selectedIdx, cell)
                    },
                    onPaste = { pasteAt(selectedIdx) },
                    onConsume = {
                        Racks.update(rackIdx, rack.replaceCell(selectedIdx, RackCell(rowLabel(selectedIdx / rack.cols), false)))
                        moving = null
                        selectedIdx = Racks.all[rackIdx].cells.indexOfFirst { it.occupied }.coerceAtLeast(0)
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (editing) {
            RackEditor(
                initial = rack,
                canDelete = Racks.all.size > 1,
                onCancel = { editing = false },
                onSave = { name, cols, rows, staggered ->
                    Racks.update(rackIdx, rack.resized(cols, rows, staggered).copy(name = name))
                    val updated = Racks.all[rackIdx]
                    selectedIdx = updated.cells.indexOfFirst { it.occupied }.coerceAtLeast(0)
                    editing = false
                },
                onDuplicate = {
                    rackIdx = Racks.duplicate(rackIdx)
                    editing = false
                },
                onDelete = {
                    Racks.remove(rackIdx)
                    rackIdx = rackIdx.coerceIn(0, Racks.all.lastIndex)
                    editing = false
                },
            )
        }
    }
}

/** Short tab label (cellar screen only): drop the redundant "Cave " prefix, truncate. */
private fun shortRackLabel(name: String): String {
    val base = name.trim().removePrefix("Cave ").trim().ifBlank { name.trim() }
    return if (base.length > 12) base.take(11).trimEnd() + "…" else base
}

@Composable
private fun CellarTabs(
    names: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            names.forEachIndexed { i, t ->
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
                        shortRackLabel(t),
                        fontSize = 12.sp, fontWeight = FontWeight.W600,
                        color = if (on) Color.White else VincentColors.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButtonBox(Icons.Filled.Edit, "Éditer le casier", onEdit)
        IconButtonBox(Icons.Filled.Add, "Ajouter un casier", onAdd)
    }
}

@Composable
private fun IconButtonBox(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = VincentColors.Accent, modifier = Modifier.size(17.dp)) }
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
private fun RackGrid(
    rack: Rack,
    rackIdx: Int,
    mode: RackMode,
    filter: RackFilter?,
    selectedIdx: Int,
    moving: Int?,
    dragFrom: Int?,
    dragHover: Int?,
    clipboard: RackClipboardEntry?,
    onCellTap: (Int) -> Unit,
    onCellBounds: (Int, Rect) -> Unit,
    onDragStart: (Int) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val moveActive = moving != null
    val dragActive = dragFrom != null
    val matching = rack.cells.count { it.occupied && (filter?.test?.invoke(it) ?: true) }
    VCard(Modifier.fillMaxWidth()) {
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
                        val cutSource = clipboard?.takeIf { it.mode == RackClipboardMode.CUT }
                        Cell(
                            cell, mode, filter,
                            selected = gi == selectedIdx,
                            moving = gi == moving,
                            dragging = gi == dragFrom,
                            cutMarked = cutSource?.rackIndex == rackIdx && cutSource.cellIndex == gi,
                            dropTarget = when {
                                dragActive -> !cell.occupied && gi == dragHover
                                moveActive -> !cell.occupied
                                else -> false
                            },
                            pasteTarget = clipboard != null && !cell.occupied && !moveActive && !dragActive,
                            onClick = { onCellTap(gi) },
                            onBoundsChanged = { onCellBounds(gi, it) },
                            onDragStart = { onDragStart(gi) },
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rack.staggered && !shiftRight) Spacer(Modifier.weight(0.5f))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        dragActive -> "Déposez sur une case vide"
                        moveActive -> "Déplacement — touchez une case vide"
                        clipboard != null -> "Presse-papiers actif — touchez une case vide pour coller"
                        filter == null -> "Maintenez pour glisser · touchez pour sélectionner"
                        else -> "$matching bouteille${if (matching > 1) "s" else ""} dans ce filtre"
                    },
                    fontSize = 11.sp,
                    color = if (dragActive || moveActive || clipboard != null) VincentColors.Accent else VincentColors.Muted,
                    fontWeight = FontWeight.W500,
                )
                Text(
                    "${(rack.occupiedCount * 100 / rack.capacity.coerceAtLeast(1))}%",
                    style = MonoNumber, color = VincentColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun Cell(
    cell: RackCell,
    mode: RackMode,
    filter: RackFilter?,
    selected: Boolean,
    moving: Boolean,
    dragging: Boolean,
    cutMarked: Boolean,
    dropTarget: Boolean,
    pasteTarget: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cellCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val boundsMod = modifier
        .onGloballyPositioned {
            cellCoords = it
            onBoundsChanged(it.boundsInRoot())
        }

    if (!cell.occupied) {
        // Empty slot: tap to add a bottle here — or, during a move, a drop target.
        Box(
            boundsMod
                .aspectRatio(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    when {
                        dropTarget -> VincentColors.AccentSoft
                        pasteTarget -> VincentColors.AccentSoft.copy(alpha = 0.45f)
                        else -> VincentColors.Surface2
                    },
                )
                .border(
                    1.dp,
                    when {
                        dropTarget -> VincentColors.Accent
                        pasteTarget -> VincentColors.Accent.copy(alpha = 0.55f)
                        else -> VincentColors.Border
                    },
                    RoundedCornerShape(7.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (pasteTarget && !dropTarget) Icons.Filled.ContentPaste else Icons.Filled.Add,
                contentDescription = when {
                    dropTarget -> "Déplacer ici"
                    pasteTarget -> "Coller ici"
                    else -> "Ajouter ici"
                },
                tint = if (dropTarget || pasteTarget) VincentColors.Accent else VincentColors.Faint,
                modifier = Modifier.size(if (pasteTarget && !dropTarget) 14.dp else 16.dp),
            )
        }
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
        boundsMod
            .aspectRatio(1f)
            .alpha(if (dragging || moving) 0.5f else if (matches) 1f else 0.32f)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(cell.occupied) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, _ ->
                        val root = cellCoords?.localToRoot(change.position) ?: change.position
                        onDragMove(root)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            }
            .clickable(onClick = onClick)
            .background(tint)
            .border(
                if (selected || moving || dragging || cutMarked) 4.dp else 3.dp,
                when {
                    cutMarked -> VincentColors.Accent
                    else -> wineBorder
                },
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Selection / move cue: inset accent ring (keeps the wine-colour border intact).
        if (selected || moving || dragging) {
            Box(
                Modifier.matchParentSize().padding(3.dp)
                    .border(1.5.dp, VincentColors.Accent, RoundedCornerShape(5.dp)),
            )
        }
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
private fun PeekCard(
    rack: Rack,
    rackIdx: Int,
    selectedIdx: Int,
    moving: Boolean,
    clipboard: RackClipboardEntry?,
    onOpenBottle: (Bottle) -> Unit,
    onAddBottle: () -> Unit,
    onMove: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onConsume: () -> Unit,
) {
    val cell = rack.cells.getOrNull(selectedIdx)
    if (cell == null || !cell.occupied) {
        val spot = RackPlacement(rackIdx, selectedIdx).spotLabel(rack.cols)
        VCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Emplacement $spot", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                Text(
                    if (clipboard != null) "Case libre — collez ou ajoutez une bouteille."
                    else "Case libre — ajoutez une bouteille ici.",
                    fontSize = 11.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(11.dp))
                if (clipboard != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PeekAction("Coller", Icons.Filled.ContentPaste, onPaste, Modifier.weight(1f))
                        PeekAction("Ajouter", Icons.Filled.Add, onAddBottle, Modifier.weight(1f))
                    }
                } else {
                    PeekAction("Ajouter bouteille", Icons.Filled.Add, onAddBottle, Modifier.fillMaxWidth())
                }
            }
        }
        return
    }
    val spot = "${rowLabel(selectedIdx / rack.cols)}${selectedIdx % rack.cols + 1}"
    // Best-effort link to a real bottle (colour + vintage + price); else info only.
    val match = Cellar.bottles.firstOrNull {
        it.color == cell.color && it.price == cell.price && it.vintage.takeLast(2) == cell.vintage?.takeLast(2)
    }
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val infoMod = if (match != null) Modifier.fillMaxWidth().clickable { onOpenBottle(match) } else Modifier.fillMaxWidth()
            Row(infoMod, verticalAlignment = Alignment.CenterVertically) {
                WineBottle(cell.color ?: WineColor.RED, Modifier.size(width = 30.dp, height = 46.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Emplacement $spot", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    Text(
                        buildString {
                            append(cell.vintage.orEmpty())
                            append(" · ${cell.price} €")
                            cell.category?.let { append(" · ${it.label}") }
                        },
                        fontSize = 11.sp, color = VincentColors.Muted,
                    )
                }
                cell.color?.let { ColorTag(it, label = cell.category?.label ?: it.label) }
            }

            Spacer(Modifier.height(11.dp))
            if (moving) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Touchez une case vide pour déplacer",
                        fontSize = 12.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent,
                        modifier = Modifier.weight(1f),
                    )
                    PeekAction("Annuler", null, onMove)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeekAction("Couper", Icons.Filled.ContentCut, onCut, Modifier.weight(1f))
                    PeekAction("Copier", Icons.Filled.ContentCopy, onCopy, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PeekAction("Déplacer", Icons.Filled.SwapHoriz, onMove, Modifier.weight(1f))
                    PeekAction("Consommer", Icons.Filled.LocalBar, onConsume, Modifier.weight(1f))
                }
                if (clipboard != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (clipboard.mode == RackClipboardMode.CUT) "Coupe en attente — choisissez une case libre"
                        else "Copie en attente — choisissez une case libre",
                        fontSize = 11.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeekAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
    }
}

@Composable
private fun RackEditor(
    initial: Rack,
    canDelete: Boolean,
    onCancel: () -> Unit,
    onSave: (String, Int, Int, Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var cols by remember { mutableIntStateOf(initial.cols) }
    var rows by remember { mutableIntStateOf(initial.rows) }
    var staggered by remember { mutableStateOf(initial.staggered) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(VincentColors.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Éditer le casier", fontSize = 16.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Nom", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Colonnes", cols, 1..10) { cols = it }
            Stepper("Rangées", rows, 1..12) { rows = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quinconce", fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                    Text("Rangées décalées d'une demi-case", fontSize = 10.5.sp, color = VincentColors.Muted)
                }
                Switch(checked = staggered, onCheckedChange = { staggered = it })
            }
            Text("Capacité : ${cols * rows} emplacements", fontSize = 11.sp, color = VincentColors.Muted)
            // Manage: clone or delete this rack.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) { Text("Dupliquer") }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VincentColors.Red),
                ) { Text("Supprimer") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(
                    onClick = { onSave(name.ifBlank { initial.name }, cols, rows, staggered) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
                ) { Text("Enregistrer") }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
        StepBtn("−") { if (value > range.first) onChange(value - 1) }
        Text("$value", Modifier.width(36.dp), style = MonoNumber, color = VincentColors.Fg, textAlign = TextAlign.Center)
        StepBtn("+") { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 17.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg) }
}
