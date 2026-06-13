package com.geoking.vincent.data

import com.geoking.vincent.model.Bottle

/**
 * Persistence seam for the cellar. [Cellar] keeps the reactive in-memory state for
 * the UI; an implementation of this interface (Room on Android — see
 * `androidMain/.../db/RoomCellarRepository`) provides durable storage behind it.
 *
 * Kept in commonMain so the UI never depends on the platform persistence engine.
 */
interface CellarRepository {
    suspend fun loadAll(): List<Bottle>
    suspend fun upsert(bottle: Bottle)
    suspend fun delete(id: String)
    suspend fun count(): Int
}
