package fr.geoking.vincent.data

import fr.geoking.vincent.model.Rack

interface RackRepository {
    suspend fun loadAll(): List<Rack>
    suspend fun upsert(rack: Rack)
    suspend fun delete(id: String)
}
