package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.Region

@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val country: String,
    val description: String,
)

fun RegionEntity.toRegion(): Region = Region(
    id = id,
    name = name,
    country = country,
    description = description,
)

fun Region.toEntity(): RegionEntity = RegionEntity(
    id = id,
    name = name,
    country = country,
    description = description,
)
