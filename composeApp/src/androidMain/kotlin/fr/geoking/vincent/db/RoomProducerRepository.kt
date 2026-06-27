package fr.geoking.vincent.db

import fr.geoking.vincent.data.ProducerRepository
import fr.geoking.vincent.model.Producer

class RoomProducerRepository(private val dao: ProducerDao) : ProducerRepository {
    override suspend fun loadAll(): List<Producer> = dao.getAll().map { it.toProducer() }
    override suspend fun upsert(producer: Producer) = dao.upsert(producer.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
