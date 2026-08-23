package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Regions {
    val all = mutableStateListOf<Region>()

    private var repo: RegionRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Bumped on [clearAll] so in-flight [persist] upserts cannot recreate wiped rows. */
    private var generation = 0

    suspend fun bootstrap(repository: RegionRepository) {
        repo = repository
        val persisted = repository.loadAll()
        all.clear(); all.addAll(persisted)
    }

    suspend fun reloadFromRepository() {
        val r = repo ?: return
        all.clear(); all.addAll(r.loadAll())
    }

    fun import(incoming: List<Region>): Int {
        incoming.forEach { r ->
            val i = all.indexOfFirst { it.id == r.id }
            if (i >= 0) all[i] = r else all.add(0, r)
            persist(r)
        }
        return incoming.size
    }

    suspend fun clearAll() {
        generation++
        all.clear()
        repo?.deleteAll()
    }

    private fun persist(r: Region) {
        val repo = repo ?: return
        val gen = generation
        scope.launch {
            if (gen != generation) return@launch
            repo.upsert(r)
            // cloudSyncPushRegion(r) // TODO: implement if needed
        }
    }
}
