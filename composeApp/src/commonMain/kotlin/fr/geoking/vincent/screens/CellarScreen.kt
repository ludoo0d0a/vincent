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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Features
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
import fr.geoking.vincent.ui.ScreenHeader
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle
import fr.geoking.vincent.ui.rackInteriorBase
import fr.geoking.vincent.ui.rackWineBorderColor
import fr.geoking.vincent.ui.rackYearOf

/** A range filter applied to the rack (dims non-matching cells). */
private data class RackFilter(val label: String, val test: (RackCell) -> Boolean)

private sealed interface RackImportStatus {
    data class Success(val count: Int) : RackImportStatus
    data object WrongType : RackImportStatus
}

private val rackFilters = listOf(
    RackFilter("≤ 2015") { (rackYearOf(it) ?: Int.MAX_VALUE) <= 2015 },
    RackFilter("2016–19") { rackYearOf(it)?.let { y -> y in 2016..2019 } == true },
    RackFilter("2020 +") { (rackYearOf(it) ?: 0) >= 2020 },
    RackFilter("≤ 25 €") { (it.price ?: Int.MAX_VALUE) <= 25 },
    RackFilter("25–50 €") { it.price?.let { p -> p in 26..50 } == true },
    RackFilter("> 50 €") { (it.price ?: 0) > 50 },
)

@Composable
fun CellarScreen(
    modifier: Modifier = Modifier,
    onOpenBottle: (Bottle) -> Unit,
    onAddToCell: (RackPlacement) -> Unit = {},
    onOpenAr: (Int) -> Unit = {},
) {
    var rackIdx by remember { mutableIntStateOf(0) }
    val rack = Racks.all[rackIdx.coerceIn(0, Racks.all.lastIndex)]
    var mode by remember { mutableStateOf(RackMode.VINTAGE) }
    var filterIdx by remember { mutableIntStateOf(-1) }
    val filter = rackFilters.getOrNull(filterIdx)
    var editing by remember { mutableStateOf(false) }
    var selectedIdx by remember(rackIdx) {
        mutableStateOf<Int?>(
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
    // Height of the bottom-pinned selected-bottle detail, so the scrolling grid
    // can reserve matching space and never hide its last rows behind it.
    val density = LocalDensity.current
    var peekHeightPx by remember { mutableIntStateOf(0) }
    val peekHeight = if (selectedIdx != null) with(density) { peekHeightPx.toDp() } else 0.dp

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

    var rackImportStatus by remember { mutableStateOf<RackImportStatus?>(null) }
    val importCsv = rememberCsvImport { text ->
        val result = CsvFormat.parse(text)
        rackImportStatus = if (result.type == CsvFormat.ImportType.RACKS) {
            result.racks.forEach { Racks.add(it) }
            RackImportStatus.Success(result.racks.size)
        } else {
            RackImportStatus.WrongType
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                stringResource(Res.string.cellar_title),
                stringResource(Res.string.cellar_subtitle_format, rack.capacity, rack.occupiedCount),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (Features.arEnabled) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable { onOpenAr(rackIdx) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.ViewInAr, contentDescription = stringResource(Res.string.ar_open), modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }
                        }
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable { importCsv() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.FileUpload, contentDescription = stringResource(Res.string.import_action), modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }
                    }
                },
            )

            Column(Modifier.padding(horizontal = 16.dp)) {
                when (val status = rackImportStatus) {
                    is RackImportStatus.Success -> Box(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                    ) { Text(pluralStringResource(Res.plurals.cellar_import_success, status.count, status.count), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                    RackImportStatus.WrongType -> Box(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                    ) { Text(stringResource(Res.string.cellar_import_wrong_type), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                    null -> Unit
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
                // Reserve room for the bottom-pinned detail card so the last rack
                // rows can always be scrolled clear of it.
                Spacer(Modifier.height(peekHeight + 24.dp))
            }
        }

        // Selected-bottle detail, pinned to the bottom. Dismissable so the whole
        // rack can be seen at a glance.
        val sel = selectedIdx
        if (sel != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(VincentColors.Bg)
                    .onGloballyPositioned { peekHeightPx = it.size.height }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                PeekCard(
                    rack, rackIdx, sel, moving == sel, clipboard, onOpenBottle,
                    onAddBottle = { onAddToCell(RackPlacement(rackIdx, sel)) },
                    onDismiss = { selectedIdx = null },
                    onMove = { moving = if (moving == sel) null else sel },
                    onCopy = {
                        val cell = rack.cells.getOrNull(sel) ?: return@PeekCard
                        if (cell.occupied) RackClipboard.copy(rackIdx, sel, cell)
                    },
                    onPaste = { pasteAt(sel) },
                    onConsume = {
                        Racks.update(rackIdx, rack.replaceCell(sel, RackCell(rowLabel(sel / rack.cols), false)))
                        moving = null
                        selectedIdx = Racks.all[rackIdx].cells.indexOfFirst { it.occupied }.coerceAtLeast(0)
                    },
                )
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
        IconButtonBox(Icons.Filled.Edit, stringResource(Res.string.cellar_edit_rack), onEdit)
        IconButtonBox(Icons.Filled.Add, stringResource(Res.string.cellar_add_rack), onAdd)
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
                        stringResource(m.label),
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
    selectedIdx: Int?,
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
                        dragActive -> stringResource(Res.string.cellar_drag_drop_hint)
                        moveActive -> stringResource(Res.string.cellar_move_hint)
                        clipboard != null -> stringResource(Res.string.cellar_paste_hint)
                        filter == null -> stringResource(Res.string.cellar_drag_manual_hint)
                        else -> pluralStringResource(Res.plurals.cellar_filter_matching_format, matching, matching)
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
                    dropTarget -> stringResource(Res.string.cellar_action_move)
                    pasteTarget -> stringResource(Res.string.cellar_action_paste)
                    else -> stringResource(Res.string.cellar_action_add)
                },
                tint = if (dropTarget || pasteTarget) VincentColors.Accent else VincentColors.Faint,
                modifier = Modifier.size(if (pasteTarget && !dropTarget) 14.dp else 16.dp),
            )
        }
        return
    }
    val matches = filter?.test?.invoke(cell) ?: true
    // Interior follows the active mode; border ALWAYS keeps the wine colour, thick.
    val tint = lerp(Color.White, rackInteriorBase(cell, mode), 0.22f)
    val wineBorder = rackWineBorderColor(cell)
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
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onConsume: () -> Unit,
) {
    val cell = rack.cells.getOrNull(selectedIdx)
    val spot = RackPlacement(rackIdx, selectedIdx).spotLabel(rack.cols)
    if (cell == null || !cell.occupied) {
        VCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.cellar_spot_label, spot), fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                        Text(
                            if (clipboard != null) stringResource(Res.string.cellar_free_spot_paste_desc)
                            else stringResource(Res.string.cellar_free_spot_desc),
                            fontSize = 11.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    PeekIconButton(Icons.Filled.Close, stringResource(Res.string.cellar_dismiss), onDismiss)
                }
                Spacer(Modifier.height(11.dp))
                if (clipboard != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PeekAction(stringResource(Res.string.cellar_action_paste), Icons.Filled.ContentPaste, onPaste, Modifier.weight(1f))
                        PeekAction(stringResource(Res.string.cellar_action_add), Icons.Filled.Add, onAddBottle, Modifier.weight(1f))
                    }
                } else {
                    PeekAction(stringResource(Res.string.cellar_action_add_bottle), Icons.Filled.Add, onAddBottle, Modifier.fillMaxWidth())
                }
            }
        }
        return
    }
    // Best-effort link to a real bottle (colour + vintage + price); else info only.
    val match = Cellar.bottles.firstOrNull {
        it.color == cell.color && it.price == cell.price && it.vintage.takeLast(2) == cell.vintage?.takeLast(2)
    }
    val categoryLabel = cell.category?.let { stringResource(it.label) }
    val colorLabel = cell.color?.let { stringResource(it.label) }
    val title = match?.domain ?: categoryLabel ?: colorLabel ?: stringResource(Res.string.cellar_spot_label, spot)
    val subtitle = buildString {
        append(spot)
        cell.vintage?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        cell.price?.let { append(" · $it €") }
    }
    var menuOpen by remember(selectedIdx) { mutableStateOf(false) }
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WineBottle(cell.color ?: WineColor.RED, Modifier.size(width = 26.dp, height = 40.dp))
                Spacer(Modifier.width(11.dp))
                val infoMod = if (match != null) Modifier.weight(1f).clickable { onOpenBottle(match) } else Modifier.weight(1f)
                Column(infoMod) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, fontSize = 11.sp, color = VincentColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    PeekIconButton(Icons.Filled.MoreVert, stringResource(Res.string.cellar_actions_menu)) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cellar_action_copy)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cellar_action_move)) },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onMove() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cellar_action_drink)) },
                            leadingIcon = { Icon(Icons.Filled.LocalBar, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onConsume() },
                        )
                    }
                }
                PeekIconButton(Icons.Filled.Close, stringResource(Res.string.cellar_dismiss), onDismiss)
            }
            if (moving) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.cellar_move_target_hint),
                        fontSize = 12.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent,
                        modifier = Modifier.weight(1f),
                    )
                    PeekAction(stringResource(Res.string.cellar_action_cancel), null, onMove)
                }
            } else if (clipboard != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (clipboard.mode == RackClipboardMode.CUT) stringResource(Res.string.cellar_cut_pending)
                    else stringResource(Res.string.cellar_copy_pending),
                    fontSize = 11.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent,
                )
            }
        }
    }
}

@Composable
private fun PeekIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = VincentColors.Muted, modifier = Modifier.size(20.dp))
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
            Text(stringResource(Res.string.cellar_edit_title), fontSize = 16.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(Res.string.cellar_edit_name), fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(),
            )
            Stepper(stringResource(Res.string.cellar_edit_cols), cols, 1..10) { cols = it }
            Stepper(stringResource(Res.string.cellar_edit_rows), rows, 1..12) { rows = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.cellar_edit_staggered), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                    Text(stringResource(Res.string.cellar_edit_staggered_desc), fontSize = 10.5.sp, color = VincentColors.Muted)
                }
                Switch(checked = staggered, onCheckedChange = { staggered = it })
            }
            Text(stringResource(Res.string.cellar_edit_capacity, cols * rows), fontSize = 11.sp, color = VincentColors.Muted)
            // Manage: clone or delete this rack.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cellar_edit_duplicate)) }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VincentColors.Red),
                ) { Text(stringResource(Res.string.cellar_edit_delete)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cellar_edit_cancel)) }
                Button(
                    onClick = { onSave(name.ifBlank { initial.name }, cols, rows, staggered) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
                ) { Text(stringResource(Res.string.cellar_edit_save)) }
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
