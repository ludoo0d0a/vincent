package com.vincent.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vincent.model.Bottle
import com.vincent.model.ColorBreakdown
import com.vincent.model.SampleData
import com.vincent.model.WineColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single source of truth for the cellar. Holds Compose snapshot state, so any
 * composable that reads [bottles] (directly or via the derived helpers) recomposes
 * when a bottle is added, served, or favourited.
 *
 * Seeded from [SampleData] for immediate UI, then bound to a [CellarRepository]
 * (Room) via [bootstrap]: persisted data replaces the seed on launch, and every
 * mutation is written through to disk. The UI never sees the persistence engine.
 */
object Cellar {

    val bottles = mutableStateListOf<Bottle>().also { it.addAll(SampleData.bottles) }
    val recent = mutableStateListOf<Bottle>().also { it.addAll(SampleData.recent) }

    var addedThisMonth by mutableStateOf(SampleData.addedThisMonth)

    private var repo: CellarRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Wire a persistent store. On first launch (empty DB) the seed is persisted;
     * afterwards the persisted bottles replace the in-memory seed. Call once at
     * app start from a coroutine.
     */
    suspend fun bootstrap(repository: CellarRepository) {
        repo = repository
        val persisted = repository.loadAll()
        if (persisted.isEmpty()) {
            bottles.forEach { repository.upsert(it) }
        } else {
            bottles.clear(); bottles.addAll(persisted)
            recent.clear(); recent.addAll(persisted.take(6))
        }
    }

    // --- reads (call inside composition to observe changes) ---

    fun bottle(id: String): Bottle? = bottles.firstOrNull { it.id == id }

    val favorites: List<Bottle> get() = bottles.filter { it.favorite }

    fun totalBottles(): Int = bottles.sumOf { it.quantity }
    fun references(): Int = bottles.size
    fun estimatedValue(): Int = bottles.sumOf { it.price * it.quantity }
    fun averagePrice(): Int = estimatedValue() / totalBottles().coerceAtLeast(1)
    fun readyToDrink(): Int = bottles.count { it.drinkTo > 0 && it.drinkNow >= 0.4f }

    fun breakdown(): List<ColorBreakdown> {
        val total = totalBottles().coerceAtLeast(1)
        return WineColor.entries
            .map { c -> ColorBreakdown(c, bottles.filter { it.color == c }.sumOf { it.quantity } * 100 / total) }
            .filter { it.percent > 0 }
    }

    /** Bottles matching an optional colour filter, used by the search/results count. */
    fun matching(color: WineColor?): List<Bottle> =
        if (color == null) bottles else bottles.filter { it.color == color }

    // --- mutations (update snapshot state immediately, persist asynchronously) ---

    fun addBottle(b: Bottle) {
        bottles.add(0, b)
        recent.add(0, b)
        addedThisMonth += 1
        persist(b)
    }

    fun adjustQuantity(id: String, delta: Int) {
        val i = bottles.indexOfFirst { it.id == id }
        if (i >= 0) {
            val updated = bottles[i].copy(quantity = (bottles[i].quantity + delta).coerceAtLeast(0))
            bottles[i] = updated
            persist(updated)
        }
    }

    fun toggleFavorite(id: String) {
        val i = bottles.indexOfFirst { it.id == id }
        if (i >= 0) {
            val updated = bottles[i].copy(favorite = !bottles[i].favorite)
            bottles[i] = updated
            persist(updated)
        }
    }

    private fun persist(b: Bottle) {
        val r = repo ?: return
        scope.launch { r.upsert(b) }
    }
}
