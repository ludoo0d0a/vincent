package fr.geoking.vincent.db

import fr.geoking.vincent.data.TastingRepository
import fr.geoking.vincent.model.Tasting

class RoomTastingRepository(private val dao: TastingDao) : TastingRepository {
    override suspend fun loadAll(): List<Tasting> = dao.getAll().map { it.toTasting() }
    override suspend fun upsert(tasting: Tasting) = dao.upsert(tasting.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
