package fr.geoking.vincent.model

/** Row label from a 0-based index: 0 → A, 1 → B … */
fun rowLabel(rowIndex: Int): String = ('A' + rowIndex).toString()

/** A chosen empty cell in a rack (used when adding from the cellar grid). */
data class RackPlacement(val rackIndex: Int, val cellIndex: Int) {
    fun spotLabel(cols: Int): String = cellSpotLabel(cellIndex, cols)
}

/** Spot label for a cell index, e.g. 0-based cell 10 in 6 cols → "B5". */
fun cellSpotLabel(cellIndex: Int, cols: Int): String =
    "${rowLabel(cellIndex / cols)}${cellIndex % cols + 1}"

/** Parse a spot label (e.g. "B3") into a cell index for the given rack width. */
fun cellIndexFromSpot(spot: String, cols: Int): Int? {
    val s = spot.trim().uppercase()
    if (s.isBlank() || s == "—") return null
    val row = s.firstOrNull()?.minus('A') ?: return null
    if (row < 0) return null
    val col = s.drop(1).toIntOrNull()?.minus(1) ?: return null
    if (col < 0) return null
    return row * cols + col
}

/** A point in normalised image space (0..1, origin top-left). Used for AR calibration. */
data class NormPoint(val x: Float, val y: Float)

/**
 * The four calibration corners of the rack face inside the reference photo, in
 * the order topLeft, topRight, bottomRight, bottomLeft (normalised 0..1). Used to
 * bilinearly place each cell over the tracked image in AR.
 */
data class RackArCalibration(val corners: List<NormPoint>) {
    val isValid: Boolean get() = corners.size == 4
}

/** A named rack: a [cols]×[rows] grid of [RackCell], optionally staggered (quinconce). */
data class Rack(
    val name: String,
    val cols: Int,
    val rows: Int,
    val staggered: Boolean,
    val cells: List<RackCell>,
    val id: String = "rack-${kotlin.math.abs(name.hashCode())}-${cols}x${rows}",
    /** Absolute path to the photo of this physical rack, used as the AR image target. */
    val arImagePath: String? = null,
    /** Calibration of the rack face within [arImagePath]; null until the user calibrates. */
    val arCalibration: RackArCalibration? = null,
) {
    /** True when this rack has been photographed and calibrated for AR. */
    val arReady: Boolean get() = arImagePath != null && arCalibration?.isValid == true
    val capacity: Int get() = cols * rows
    val occupiedCount: Int get() = cells.count { it.occupied }

    /** Resize to [newCols]×[newRows], keeping existing cells by position, padding empties. */
    fun resized(newCols: Int, newRows: Int, newStaggered: Boolean): Rack {
        val out = ArrayList<RackCell>(newCols * newRows)
        for (r in 0 until newRows) for (c in 0 until newCols) {
            val old = if (c < cols && r < rows) cells.getOrNull(r * cols + c) else null
            out += old ?: RackCell(rowLabel(r), false)
        }
        return copy(cols = newCols, rows = newRows, staggered = newStaggered, cells = out)
    }

    /** Replace one cell (e.g. empty it after consuming the bottle). */
    fun replaceCell(index: Int, cell: RackCell): Rack =
        if (index !in cells.indices) this
        else copy(cells = cells.toMutableList().also { it[index] = cell })

    /** Move the bottle from [from] to an empty [to]; [from] becomes empty. */
    fun moveCell(from: Int, to: Int): Rack {
        if (from !in cells.indices || to !in cells.indices || from == to) return this
        val list = cells.toMutableList()
        list[to] = list[from].copy(selected = false)
        list[from] = RackCell(rowLabel(from / cols), false)
        return copy(cells = list)
    }
}

/** An all-empty rack of the given size. */
fun emptyRack(name: String, cols: Int, rows: Int, staggered: Boolean): Rack =
    Rack(name, cols, rows, staggered, List(cols * rows) { RackCell(rowLabel(it / cols), false) })
