package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Tasting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Tastings {
    val all = mutableStateListOf<Tasting>()

    private var repo: TastingRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun bootstrap(repository: TastingRepository) {
        repo = repository
        val persisted = repository.loadAll()
        all.clear(); all.addAll(persisted)
    }

    suspend fun reloadFromRepository() {
        val r = repo ?: return
        all.clear(); all.addAll(r.loadAll())
    }

    fun import(incoming: List<Tasting>): Int {
        incoming.forEach { save(it) }
        return incoming.size
    }

    fun save(t: Tasting) {
        val i = all.indexOfFirst { it.id == t.id }
        if (i >= 0) all[i] = t else all.add(0, t)
        persist(t)
    }

    fun delete(id: String) {
        val i = all.indexOfFirst { it.id == id }
        if (i >= 0) {
            all.removeAt(i)
            val repo = repo ?: return
            scope.launch { repo.delete(id) }
        }
    }

    suspend fun clearAll() {
        val r = repo ?: return
        all.toList().forEach { r.delete(it.id) }
        all.clear()
    }

    private fun persist(t: Tasting) {
        val repo = repo ?: return
        scope.launch {
            repo.upsert(t)
            cloudSyncPushTasting(t)
        }
    }
}
