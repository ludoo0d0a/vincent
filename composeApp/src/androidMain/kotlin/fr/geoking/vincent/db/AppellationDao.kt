package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppellationDao {
    @Query("SELECT * FROM appellations")
    suspend fun getAll(): List<AppellationEntity>

    @Query("SELECT COUNT(*) FROM appellations")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(appellation: AppellationEntity)

    @Query("DELETE FROM appellations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM appellations")
    suspend fun deleteAll()
}
