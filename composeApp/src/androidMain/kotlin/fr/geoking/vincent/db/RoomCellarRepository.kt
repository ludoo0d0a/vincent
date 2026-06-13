package fr.geoking.vincent.db

import fr.geoking.vincent.data.CellarRepository
import fr.geoking.vincent.model.Bottle

/** Room-backed implementation of the [CellarRepository] seam. */
class RoomCellarRepository(private val dao: BottleDao) : CellarRepository {

    override suspend fun loadAll(): List<Bottle> = dao.getAll().map { it.toBottle() }

    override suspend fun upsert(bottle: Bottle) = dao.upsert(bottle.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun count(): Int = dao.count()
}
