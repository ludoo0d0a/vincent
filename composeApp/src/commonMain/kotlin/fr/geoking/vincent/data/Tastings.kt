package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Tasting

object Tastings {
    val all = mutableStateListOf<Tasting>()

    fun import(incoming: List<Tasting>): Int {
        incoming.forEach { t ->
            val i = all.indexOfFirst { it.id == t.id }
            if (i >= 0) all[i] = t else all.add(0, t)
        }
        return incoming.size
    }
}
