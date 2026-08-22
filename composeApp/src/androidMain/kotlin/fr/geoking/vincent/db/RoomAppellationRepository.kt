package fr.geoking.vincent.db

import fr.geoking.vincent.data.AppellationRepository
import fr.geoking.vincent.model.Appellation

class RoomAppellationRepository(private val dao: AppellationDao) : AppellationRepository {
    override suspend fun loadAll(): List<Appellation> = dao.getAll().map { it.toAppellation() }
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(appellation: Appellation) = dao.upsert(appellation.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
