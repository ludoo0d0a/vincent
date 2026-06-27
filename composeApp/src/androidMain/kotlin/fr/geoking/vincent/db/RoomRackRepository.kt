package fr.geoking.vincent.db

import fr.geoking.vincent.data.RackRepository
import fr.geoking.vincent.model.Rack

class RoomRackRepository(private val dao: RackDao) : RackRepository {
    override suspend fun loadAll(): List<Rack> = dao.getAll().map { it.toRack() }
    override suspend fun upsert(rack: Rack) = dao.upsert(rack.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
