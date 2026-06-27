package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RackDao {
    @Query("SELECT * FROM racks")
    suspend fun getAll(): List<RackEntity>

    @Upsert
    suspend fun upsert(rack: RackEntity)

    @Query("DELETE FROM racks WHERE id = :id")
    suspend fun delete(id: String)
}
