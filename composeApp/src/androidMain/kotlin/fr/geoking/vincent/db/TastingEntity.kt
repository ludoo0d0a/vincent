package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.model.WineColor

@Entity(tableName = "tastings")
data class TastingEntity(
    @PrimaryKey val id: String,
    val bottleId: String?,
    val wineName: String,
    val date: String,
    val rating: Double,
    val notes: String,
    val color: String?,
    val vintage: String?,
)

fun TastingEntity.toTasting(): Tasting = Tasting(
    id = id,
    bottleId = bottleId,
    wineName = wineName,
    date = date,
    rating = rating,
    notes = notes,
    color = color?.let { WineColor.valueOf(it) },
    vintage = vintage,
)

fun Tasting.toEntity(): TastingEntity = TastingEntity(
    id = id,
    bottleId = bottleId,
    wineName = wineName,
    date = date,
    rating = rating,
    notes = notes,
    color = color?.name,
    vintage = vintage,
)
