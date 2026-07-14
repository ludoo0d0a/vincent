package fr.geoking.vincent.data

import fr.geoking.vincent.model.Tasting

interface TastingRepository {
    suspend fun loadAll(): List<Tasting>
    suspend fun upsert(tasting: Tasting)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
