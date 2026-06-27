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

    suspend fun bootstrap(repository: ProducerRepository) {
        repo = repository
        val persisted = repository.loadAll()
        all.clear(); all.addAll(persisted)
    }

    fun import(incoming: List<Producer>): Int {
        incoming.forEach { p ->
            val i = all.indexOfFirst { it.id == p.id }
            if (i >= 0) all[i] = p else all.add(0, p)
            persist(p)
        }
        return incoming.size
    }

    private fun persist(p: Producer) {
        val repo = repo ?: return
        scope.launch { repo.upsert(p) }
    }
}
