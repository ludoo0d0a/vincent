package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProducerDao {
    @Query("SELECT * FROM producers")
    suspend fun getAll(): List<ProducerEntity>

    @Upsert
    suspend fun upsert(producer: ProducerEntity)

    @Query("DELETE FROM producers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM producers")
    suspend fun deleteAll()
}
