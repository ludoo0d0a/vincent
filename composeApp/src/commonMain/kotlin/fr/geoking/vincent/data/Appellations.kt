package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Appellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Appellations {
    val all = mutableStateListOf<Appellation>()

    private var repo: AppellationRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Local directory name under app files dir for downloaded GeoJSON map pack. */
    const val MAP_PACK_DIR = "appellations-map-fr"

    suspend fun bootstrap(repository: AppellationRepository) {
        repo = repository
        val persisted = repository.loadAll()
        all.clear()
        all.addAll(persisted)
    }

    suspend fun reloadFromRepository() {
        val r = repo ?: return
        all.clear()
        all.addAll(r.loadAll())
    }

    fun import(incoming: List<Appellation>): Int {
        incoming.forEach { a ->
            val i = all.indexOfFirst { it.id == a.id }
            if (i >= 0) all[i] = a else all.add(0, a)
            persist(a)
        }
        return incoming.size
    }

    fun search(prefix: String, limit: Int = 30): List<Appellation> {
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return all.take(limit)
        return all.asSequence()
            .filter { it.name.lowercase().contains(q) || it.sign.lowercase().contains(q) }
            .sortedBy { it.name.lowercase() }
            .take(limit)
            .toList()
    }

    suspend fun clearAll() {
        repo?.deleteAll()
        all.clear()
    }

    private fun persist(a: Appellation) {
        val repository = repo ?: return
        scope.launch { repository.upsert(a) }
    }
}
