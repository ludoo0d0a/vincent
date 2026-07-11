package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TastingDao {
    @Query("SELECT * FROM tastings")
    suspend fun getAll(): List<TastingEntity>

    @Upsert
    suspend fun upsert(tasting: TastingEntity)

    @Query("DELETE FROM tastings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tastings")
    suspend fun deleteAll()
}
