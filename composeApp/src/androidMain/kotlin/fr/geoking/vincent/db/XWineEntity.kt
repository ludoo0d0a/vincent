package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A row from the embedded X-Wines dataset (downloaded on demand). Only the fields
 * useful for text search / prefill are kept; all are plain columns (no converters).
 */
@Entity(tableName = "xwines")
data class XWineEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val type: String,
    val grapes: String,
    val country: String,
    val region: String,
    val winery: String,
)
