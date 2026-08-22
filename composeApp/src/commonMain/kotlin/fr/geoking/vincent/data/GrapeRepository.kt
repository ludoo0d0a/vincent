package fr.geoking.vincent.data

import fr.geoking.vincent.model.Grape

interface GrapeRepository {
    suspend fun loadAll(): List<Grape>
    suspend fun count(): Int
    suspend fun upsert(grape: Grape)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
