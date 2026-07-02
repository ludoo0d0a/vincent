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

    private fun persist(s: Supplier) {
        val repo = repo ?: return
        scope.launch {
            repo.upsert(s)
            cloudSyncPushSupplier(s)
        }
    }
}
