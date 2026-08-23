package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Producer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Producers {
    val all = mutableStateListOf<Producer>()

    private var repo: ProducerRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Bumped on [clearAll] so in-flight [persist] upserts cannot recreate wiped rows. */
    private var generation = 0

    suspend fun bootstrap(repository: ProducerRepository) {
        repo = repository
        val persisted = repository.loadAll()
        all.clear(); all.addAll(persisted)
    }

    suspend fun reloadFromRepository() {
        val r = repo ?: return
        all.clear(); all.addAll(r.loadAll())
    }

    fun import(incoming: List<Producer>): Int {
        incoming.forEach { p ->
            val i = all.indexOfFirst { it.id == p.id }
            if (i >= 0) all[i] = p else all.add(0, p)
            persist(p)
        }
        return incoming.size
    }

    suspend fun clearAll() {
        generation++
        all.clear()
        repo?.deleteAll()
    }

    private fun persist(p: Producer) {
        val repo = repo ?: return
        val gen = generation
        scope.launch {
            if (gen != generation) return@launch
            repo.upsert(p)
            cloudSyncPushProducer(p)
        }
    }
}
