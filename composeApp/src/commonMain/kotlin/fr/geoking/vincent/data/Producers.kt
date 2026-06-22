package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Producer

object Producers {
    val all = mutableStateListOf<Producer>()

    fun import(incoming: List<Producer>): Int {
        incoming.forEach { p ->
            val i = all.indexOfFirst { it.id == p.id }
            if (i >= 0) all[i] = p else all.add(0, p)
        }
        return incoming.size
    }
}
