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

/**
 * How a rack is localised for the AR overlay:
 * - [PHOTO]: no ARCore; a 2D overlay drawn on the frozen reference photo (works everywhere).
 * - [MARKERS]: two Augmented Image markers (printed and stuck on the rack's top-left and
 *   bottom-right corners) are detected live; the rack's physical width/height are derived from
 *   their 3D positions, and the grid is stored relative to the top-left marker beacon so it
 *   re-localises across sessions, offline.
 */
enum class ArMode { PHOTO, MARKERS }

/**
 * Persistent AR anchor for [ArMode.MARKERS]: the top-left marker (the re-localisation beacon) plus
 * the transform from that marker frame to the grid centre and the grid's physical dimensions
 * (derived from the two detected markers). The transform is a translation + a unit quaternion.
 *
 * The markers are BoofCV square-binary fiducials identified by their decoded numeric ids
 * ([tlFiducialId] is the origin/beacon, [brFiducialId] the opposite corner), so they are fully
 * described by their id and never need to be stored as images.
 */
data class RackArAnchor(
    val markerId: String,
    val markerWidthMeters: Float,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
    val gridWidthMeters: Float,
    val gridHeightMeters: Float,
    /** Decoded id of the top-left (origin/beacon) square-binary fiducial. */
    val tlFiducialId: Int = -1,
    /** Decoded id of the bottom-right square-binary fiducial. */
    val brFiducialId: Int = -1,
) {
    val isValid: Boolean
        get() = markerId.isNotBlank() && markerWidthMeters > 0f &&
            gridWidthMeters > 0f && gridHeightMeters > 0f &&
            tlFiducialId >= 0 && brFiducialId >= 0
}

/** Physical layout of a rack. */
enum class RackFormat {
    /** Regular [cols]×[rows] grid of single-bottle cells (optionally staggered). */
    GRID,

    /**
     * "Casier en X": [cols]/2 × [rows]/2 squares, each grouping a 2×2 block of single-bottle
     * cells drawn with a diagonal cross, the four bottles arranged en quinconce.
     * [cols] and [rows] are therefore always even.
     */
    X,
}

/** A named rack: a [cols]×[rows] grid of [RackCell], optionally staggered (quinconce). */
data class Rack(
    val name: String,
    val cols: Int,
    val rows: Int,
    val staggered: Boolean,
    val cells: List<RackCell>,
    /** Physical layout; [RackFormat.X] groups cells into 2×2 X-bins. */
    val format: RackFormat = RackFormat.GRID,
    /**
     * Parity of the quinconce shift (only meaningful when [staggered]). false ⇒ first row (A)
     * sits flush in the corner; true ⇒ first row is shifted half a cell.
     */
    val staggerOffset: Boolean = false,
    val id: String = "rack-${kotlin.math.abs(name.hashCode())}-${cols}x${rows}",
    /** Absolute path to the photo of this physical rack, used as the AR image target. */
    val arImagePath: String? = null,
    /** Calibration of the rack face within [arImagePath]; null until the user calibrates. */
    val arCalibration: RackArCalibration? = null,
    /** Which AR localisation strategy this rack uses. */
    val arMode: ArMode = ArMode.PHOTO,
    /** Marker + grid transform for [ArMode.MARKERS]; null until calibrated. */
    val arAnchor: RackArAnchor? = null,
) {
    /** True when this rack has been calibrated for AR in its current [arMode]. */
    val arReady: Boolean
        get() = when (arMode) {
            ArMode.PHOTO -> arImagePath != null && arCalibration?.isValid == true
            ArMode.MARKERS -> arAnchor?.isValid == true
        }
    val capacity: Int get() = cols * rows
    val occupiedCount: Int get() = cells.count { it.occupied }

    /** Number of X-bin squares across; only meaningful when [format] is [RackFormat.X]. */
    val squareCols: Int get() = cols / 2

    /** Number of X-bin squares down; only meaningful when [format] is [RackFormat.X]. */
    val squareRows: Int get() = rows / 2

    /** Resize to [newCols]×[newRows], keeping existing cells by position, padding empties. */
    fun resized(
        newCols: Int,
        newRows: Int,
        newStaggered: Boolean,
        newFormat: RackFormat = format,
        newStaggerOffset: Boolean = staggerOffset,
    ): Rack {
        val out = ArrayList<RackCell>(newCols * newRows)
        for (r in 0 until newRows) for (c in 0 until newCols) {
            val old = if (c < cols && r < rows) cells.getOrNull(r * cols + c) else null
            out += old ?: RackCell(rowLabel(r), false)
        }
        return copy(
            cols = newCols,
            rows = newRows,
            staggered = newStaggered,
            format = newFormat,
            staggerOffset = newStaggerOffset,
            cells = out,
        )
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
