package fr.geoking.vincent.data

import fr.geoking.vincent.model.Producer

interface ProducerRepository {
    suspend fun loadAll(): List<Producer>
    suspend fun upsert(producer: Producer)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
