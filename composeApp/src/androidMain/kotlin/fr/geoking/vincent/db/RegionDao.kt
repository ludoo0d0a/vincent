package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RegionDao {
    @Query("SELECT * FROM regions")
    suspend fun getAll(): List<RegionEntity>

    @Upsert
    suspend fun upsert(region: RegionEntity)

    @Query("DELETE FROM regions WHERE id = :id")
    suspend fun delete(id: String)
}
