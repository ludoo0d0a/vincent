package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.Grape

private const val ALIAS_SEP = "\u001F"

@Entity(tableName = "grapes")
data class GrapeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val vivcNumber: Int,
    val country: String,
    val aliases: String,
)

fun GrapeEntity.toGrape(): Grape = Grape(
    id = id,
    name = name,
    color = color,
    vivcNumber = vivcNumber,
    country = country,
    aliases = if (aliases.isEmpty()) emptyList() else aliases.split(ALIAS_SEP),
)

fun Grape.toEntity(): GrapeEntity = GrapeEntity(
    id = id,
    name = name,
    color = color,
    vivcNumber = vivcNumber,
    country = country,
    aliases = aliases.joinToString(ALIAS_SEP),
)
