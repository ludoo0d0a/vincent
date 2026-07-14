package fr.geoking.vincent.db

import fr.geoking.vincent.data.RegionRepository
import fr.geoking.vincent.model.Region

class RoomRegionRepository(private val dao: RegionDao) : RegionRepository {
    override suspend fun loadAll(): List<Region> = dao.getAll().map { it.toRegion() }
    override suspend fun upsert(region: Region) = dao.upsert(region.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
