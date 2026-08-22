package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface GrapeDao {
    @Query("SELECT * FROM grapes")
    suspend fun getAll(): List<GrapeEntity>

    @Query("SELECT COUNT(*) FROM grapes")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(grape: GrapeEntity)

    @Query("DELETE FROM grapes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM grapes")
    suspend fun deleteAll()
}
