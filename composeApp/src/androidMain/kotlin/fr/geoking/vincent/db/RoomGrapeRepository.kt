package fr.geoking.vincent.db

import fr.geoking.vincent.data.GrapeRepository
import fr.geoking.vincent.model.Grape

class RoomGrapeRepository(private val dao: GrapeDao) : GrapeRepository {
    override suspend fun loadAll(): List<Grape> = dao.getAll().map { it.toGrape() }
    override suspend fun count(): Int = dao.count()
    override suspend fun upsert(grape: Grape) = dao.upsert(grape.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
