package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.SampleData

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
}
