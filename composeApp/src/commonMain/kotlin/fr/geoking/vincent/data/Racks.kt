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
}
