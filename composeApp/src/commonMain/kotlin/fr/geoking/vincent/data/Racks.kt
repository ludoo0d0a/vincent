package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.SampleData
import fr.geoking.vincent.model.rowLabel

/** Reactive list of racks, seeded from [SampleData] and editable at runtime. */
object Racks {
    val all = mutableStateListOf<Rack>().also { it.addAll(SampleData.seedRacks()) }

    fun update(index: Int, rack: Rack) {
        if (index in all.indices) all[index] = rack
    }

    fun add(rack: Rack) {
        all.add(rack)
    }

    /** Insert a copy of the rack at [index] right after it (name suffixed "copie"). */
    fun duplicate(index: Int): Int {
        val src = all.getOrNull(index) ?: return index
        all.add(index + 1, src.copy(name = "${src.name} (copie)"))
        return index + 1
    }

    /** Remove the rack at [index]; never removes the last remaining rack. */
    fun remove(index: Int) {
        if (all.size > 1 && index in all.indices) all.removeAt(index)
    }

    /** Move a bottle from one cell to an empty cell, possibly across racks. */
    fun moveBetween(fromRack: Int, fromCell: Int, toRack: Int, toCell: Int): Boolean {
        val src = all.getOrNull(fromRack) ?: return false
        val dst = all.getOrNull(toRack) ?: return false
        if (fromCell !in src.cells.indices || toCell !in dst.cells.indices || fromRack == toRack && fromCell == toCell) {
            return false
        }
        val bottle = src.cells[fromCell]
        if (!bottle.occupied || dst.cells[toCell].occupied) return false

        if (fromRack == toRack) {
            update(fromRack, src.moveCell(fromCell, toCell))
        } else {
            update(toRack, dst.replaceCell(toCell, bottle.copy(selected = false)))
            update(
                fromRack,
                src.replaceCell(fromCell, RackCell(rowLabel(fromCell / src.cols), false)),
            )
        }
        return true
    }

    /** Paste [cell] into an empty slot; clears the cut source when [cutSource] is set. */
    fun pasteCell(
        toRack: Int,
        toCell: Int,
        cell: RackCell,
        cutSource: Pair<Int, Int>?,
    ): Boolean {
        val dst = all.getOrNull(toRack) ?: return false
        if (toCell !in dst.cells.indices || !cell.occupied || dst.cells[toCell].occupied) return false

        update(toRack, dst.replaceCell(toCell, cell.copy(selected = false)))
        if (cutSource != null) {
            val (fromRack, fromCell) = cutSource
            val src = all.getOrNull(fromRack) ?: return true
            if (fromCell in src.cells.indices) {
                update(
                    fromRack,
                    src.replaceCell(fromCell, RackCell(rowLabel(fromCell / src.cols), false)),
                )
            }
        }
        return true
    }
}
