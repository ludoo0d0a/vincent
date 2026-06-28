package fr.geoking.vincent.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XWineDao {
    @Query("SELECT COUNT(*) FROM xwines")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM xwines WHERE name LIKE '%' || :q || '%' " +
            "OR winery LIKE '%' || :q || '%' " +
            "OR grapes LIKE '%' || :q || '%' " +
            "OR region LIKE '%' || :q || '%' " +
            "LIMIT :limit",
    )
    suspend fun search(q: String, limit: Int = 20): List<XWineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wines: List<XWineEntity>)

    @Query("DELETE FROM xwines")
    suspend fun clear()
}
