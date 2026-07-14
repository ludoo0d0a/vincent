package fr.geoking.vincent.data

import fr.geoking.vincent.model.Region

interface RegionRepository {
    suspend fun loadAll(): List<Region>
    suspend fun upsert(region: Region)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
