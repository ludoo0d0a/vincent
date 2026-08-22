package fr.geoking.vincent.data

import fr.geoking.vincent.model.Appellation

interface AppellationRepository {
    suspend fun loadAll(): List<Appellation>
    suspend fun count(): Int
    suspend fun upsert(appellation: Appellation)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
