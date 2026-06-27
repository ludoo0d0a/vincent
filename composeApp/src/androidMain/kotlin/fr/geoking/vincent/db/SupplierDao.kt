package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers")
    suspend fun getAll(): List<SupplierEntity>

    @Upsert
    suspend fun upsert(supplier: SupplierEntity)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun delete(id: String)
}
