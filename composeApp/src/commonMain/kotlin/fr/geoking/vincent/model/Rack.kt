package fr.geoking.vincent.model

/** Row label from a 0-based index: 0 → A, 1 → B … */
fun rowLabel(rowIndex: Int): String = ('A' + rowIndex).toString()

/** A named rack: a [cols]×[rows] grid of [RackCell], optionally staggered (quinconce). */
data class Rack(
    val name: String,
    val cols: Int,
    val rows: Int,
    val staggered: Boolean,
    val cells: List<RackCell>,
) {
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
}

/** An all-empty rack of the given size. */
fun emptyRack(name: String, cols: Int, rows: Int, staggered: Boolean): Rack =
    Rack(name, cols, rows, staggered, List(cols * rows) { RackCell(rowLabel(it / cols), false) })
