package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.ArMode
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackArAnchor
import fr.geoking.vincent.model.RackArCalibration
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.SampleData
import fr.geoking.vincent.model.rowLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Reactive list of racks, seeded from [SampleData] and editable at runtime. */
object Racks {
    val all = mutableStateListOf<Rack>().also { it.addAll(SampleData.seedRacks()) }

    private var repo: RackRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Wire a persistent store. If empty, the seed is persisted. */
    suspend fun bootstrap(repository: RackRepository) {
        repo = repository
        val persisted = repository.loadAll()
        if (persisted.isEmpty()) {
            all.forEach { repository.upsert(it) }
        } else {
            all.clear(); all.addAll(persisted)
        }
    }

    fun update(index: Int, rack: Rack) {
        if (index in all.indices) {
            all[index] = rack
            persist(rack)
        }
    }

    /** Attach (or update) the AR reference photo + calibration for the rack at [index]. */
    fun setArCalibration(index: Int, imagePath: String, calibration: RackArCalibration) {
        val rack = all.getOrNull(index) ?: return
        val updated = rack.copy(arImagePath = imagePath, arCalibration = calibration)
        all[index] = updated
        persist(updated)
    }

    /** Set the AR localisation mode for the rack at [index]. */
    fun setArMode(index: Int, mode: ArMode) {
        val rack = all.getOrNull(index) ?: return
        if (rack.arMode == mode) return
        val updated = rack.copy(arMode = mode)
        all[index] = updated
        persist(updated)
    }

    /** Attach (or update) the marker anchor used by the MARKERS mode. */
    fun setArAnchor(index: Int, anchor: RackArAnchor) {
        val rack = all.getOrNull(index) ?: return
        val updated = rack.copy(arAnchor = anchor)
        all[index] = updated
        persist(updated)
    }

    fun add(rack: Rack) {
        all.add(rack)
        persist(rack)
    }

    /** Insert a copy of the rack at [index] right after it (name suffixed "copie"). */
    fun duplicate(index: Int): Int {
        val src = all.getOrNull(index) ?: return index
        val updated = src.copy(name = "${src.name} (copie)", id = "rack-${src.id}-copy-${System.currentTimeMillis()}")
        all.add(index + 1, updated)
        persist(updated)
        return index + 1
    }

    /** Remove the rack at [index]; never removes the last remaining rack. */
    fun remove(index: Int) {
        if (all.size > 1 && index in all.indices) {
            val removed = all.removeAt(index)
            val r = repo ?: return
            scope.launch { r.delete(removed.id) }
        }
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

    private fun persist(r: Rack) {
        val repo = repo ?: return
        scope.launch { repo.upsert(r) }
    }
}
