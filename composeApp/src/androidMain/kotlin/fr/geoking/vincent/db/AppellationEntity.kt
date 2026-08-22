package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.Appellation

@Entity(tableName = "appellations")
data class AppellationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sign: String,
    val category: String,
    val department: String,
    val inaoId: Int,
    val geoAsset: String,
)

fun AppellationEntity.toAppellation(): Appellation = Appellation(
    id = id,
    name = name,
    sign = sign,
    category = category,
    department = department,
    inaoId = inaoId,
    geoAsset = geoAsset,
)

fun Appellation.toEntity(): AppellationEntity = AppellationEntity(
    id = id,
    name = name,
    sign = sign,
    category = category,
    department = department,
    inaoId = inaoId,
    geoAsset = geoAsset,
)
