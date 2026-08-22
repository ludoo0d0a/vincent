package fr.geoking.vincent.data

import androidx.compose.runtime.mutableStateListOf
import fr.geoking.vincent.model.Grape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Grapes {
    val all = mutableStateListOf<Grape>()

    private var repo: GrapeRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun bootstrap(repository: GrapeRepository, seedIfEmpty: suspend () -> List<Grape> = { emptyList() }) {
        repo = repository
        val persisted = repository.loadAll()
        if (persisted.isEmpty()) {
            val seed = seedIfEmpty()
            if (seed.isNotEmpty()) import(seed)
        } else {
            all.clear()
            all.addAll(persisted)
        }
    }

    suspend fun reloadFromRepository() {
        val r = repo ?: return
        all.clear()
        all.addAll(r.loadAll())
    }

    fun import(incoming: List<Grape>): Int {
        incoming.forEach { g ->
            val i = all.indexOfFirst { it.id == g.id }
            if (i >= 0) all[i] = g else all.add(0, g)
            persist(g)
        }
        return incoming.size
    }

    /** Prefix search on name and aliases (case-insensitive). */
    fun search(prefix: String, limit: Int = 20): List<Grape> {
        val q = prefix.trim().lowercase()
        if (q.length < 2) return emptyList()
        return all.asSequence()
            .filter { g ->
                g.name.lowercase().contains(q) ||
                    g.aliases.any { it.lowercase().contains(q) }
            }
            .sortedBy { it.name.lowercase() }
            .take(limit)
            .toList()
    }

    fun suggestionNames(prefix: String, limit: Int = 12): List<String> {
        val fromDb = search(prefix, limit).map { it.name }
        if (fromDb.isNotEmpty()) return fromDb
        return PopularGrapeNames.filter { it.lowercase().contains(prefix.trim().lowercase()) }.take(limit)
    }

    suspend fun clearAll() {
        repo?.deleteAll()
        all.clear()
    }

    private fun persist(g: Grape) {
        val repository = repo ?: return
        scope.launch { repository.upsert(g) }
    }
}

/** Fallback when Room is empty — mirrors [PopularGrapes] in BottleFormPickers. */
val PopularGrapeNames = listOf(
    "Cabernet Sauvignon", "Merlot", "Pinot Noir", "Syrah", "Grenache", "Chardonnay",
    "Sauvignon Blanc", "Chenin", "Riesling", "Gamay", "Viognier", "Carignan",
    "Mourvèdre", "Cinsault", "Semillon", "Muscat", "Malbec", "Tempranillo",
)
