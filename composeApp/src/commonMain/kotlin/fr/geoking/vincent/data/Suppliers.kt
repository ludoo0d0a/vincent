package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Supplier

object Suppliers {
    val all = mutableStateListOf<Supplier>()

    fun import(incoming: List<Supplier>): Int {
        incoming.forEach { s ->
            val i = all.indexOfFirst { it.id == s.id }
            if (i >= 0) all[i] = s else all.add(0, s)
        }
        return incoming.size
    }
}
