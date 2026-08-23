package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Suppliers {
    val all = mutableStateListOf<Supplier>()

    private var repo: SupplierRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Bumped on [clearAll] so in-flight [persist] upserts cannot recreate wiped rows. */
    private var generation = 0

    suspend fun bootstrap(repository: SupplierRepository) {
        repo = repository
        val persisted = repository.loadAll()
        all.clear(); all.addAll(persisted)
    }

    suspend fun reloadFromRepository() {
        val r = repo ?: return
        all.clear(); all.addAll(r.loadAll())
    }

    fun import(incoming: List<Supplier>): Int {
        incoming.forEach { s ->
            val i = all.indexOfFirst { it.id == s.id }
            if (i >= 0) all[i] = s else all.add(0, s)
            persist(s)
        }
        return incoming.size
    }

    suspend fun clearAll() {
        generation++
        all.clear()
        repo?.deleteAll()
    }

    private fun persist(s: Supplier) {
        val repo = repo ?: return
        val gen = generation
        scope.launch {
            if (gen != generation) return@launch
            repo.upsert(s)
            cloudSyncPushSupplier(s)
        }
    }
}
