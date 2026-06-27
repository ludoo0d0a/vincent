package fr.geoking.vincent.data

import fr.geoking.vincent.model.Supplier

interface SupplierRepository {
    suspend fun loadAll(): List<Supplier>
    suspend fun upsert(supplier: Supplier)
    suspend fun delete(id: String)
}
