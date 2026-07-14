package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BottleDao {
    @Query("SELECT * FROM bottles")
    suspend fun getAll(): List<BottleEntity>

    @Upsert
    suspend fun upsert(bottle: BottleEntity)

    @Query("DELETE FROM bottles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM bottles")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM bottles")
    suspend fun count(): Int
}
