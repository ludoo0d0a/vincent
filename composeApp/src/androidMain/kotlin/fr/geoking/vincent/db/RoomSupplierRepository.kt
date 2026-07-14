package fr.geoking.vincent.db

import fr.geoking.vincent.data.SupplierRepository
import fr.geoking.vincent.model.Supplier

class RoomSupplierRepository(private val dao: SupplierDao) : SupplierRepository {
    override suspend fun loadAll(): List<Supplier> = dao.getAll().map { it.toSupplier() }
    override suspend fun upsert(supplier: Supplier) = dao.upsert(supplier.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
