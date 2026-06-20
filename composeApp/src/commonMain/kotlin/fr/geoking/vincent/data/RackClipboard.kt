package fr.geoking.vincent.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.geoking.vincent.model.RackCell

enum class RackClipboardMode { COPY, CUT }

data class RackClipboardEntry(
    val rackIndex: Int,
    val cellIndex: Int,
    val cell: RackCell,
    val mode: RackClipboardMode,
)

/** In-memory clipboard for moving or duplicating a bottle between rack cells (and racks). */
object RackClipboard {
    var entry by mutableStateOf<RackClipboardEntry?>(null)
        private set

    val hasEntry: Boolean get() = entry != null

    fun copy(rackIndex: Int, cellIndex: Int, cell: RackCell) {
        entry = RackClipboardEntry(rackIndex, cellIndex, cell, RackClipboardMode.COPY)
    }

    fun cut(rackIndex: Int, cellIndex: Int, cell: RackCell) {
        entry = RackClipboardEntry(rackIndex, cellIndex, cell, RackClipboardMode.CUT)
    }

    fun clear() {
        entry = null
    }
}
