package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor

/**
 * Room row for a bottle. Enums are stored by name and the pairings list as a
 * delimiter-joined string, so no TypeConverter is needed — only primitive columns.
 */
@Entity(tableName = "bottles")
data class BottleEntity(
    @PrimaryKey val id: String,
    val domain: String,
    val appellation: String,
    val color: String,
    val category: String,
    val vintage: String,
    val price: Int,
    val quantity: Int,
    val rating: Double,
    val cellarSpot: String,
    val provenance: String,
    val merchant: String,
    val purchaseDate: String,
    val occasion: String,
    val favorite: Boolean,
    val pairings: String,
    val drinkFrom: Int,
    val drinkTo: Int,
    val drinkNow: Float,
    val tastingNotes: String,
    val source: String,
    val addedLabel: String,
)

private const val SEP = "" // unit separator — won't appear in labels

fun BottleEntity.toBottle(): Bottle = Bottle(
    id = id,
    domain = domain,
    appellation = appellation,
    color = WineColor.valueOf(color),
    category = WineCategory.valueOf(category),
    vintage = vintage,
    price = price,
    quantity = quantity,
    rating = rating,
    cellarSpot = cellarSpot,
    provenance = provenance,
    merchant = merchant,
    purchaseDate = purchaseDate,
    occasion = occasion,
    favorite = favorite,
    pairings = if (pairings.isEmpty()) emptyList() else pairings.split(SEP),
    drinkFrom = drinkFrom,
    drinkTo = drinkTo,
    drinkNow = drinkNow,
    tastingNotes = tastingNotes,
    source = AddSource.valueOf(source),
    addedLabel = addedLabel,
)

fun Bottle.toEntity(): BottleEntity = BottleEntity(
    id = id,
    domain = domain,
    appellation = appellation,
    color = color.name,
    category = category.name,
    vintage = vintage,
    price = price,
    quantity = quantity,
    rating = rating,
    cellarSpot = cellarSpot,
    provenance = provenance,
    merchant = merchant,
    purchaseDate = purchaseDate,
    occasion = occasion,
    favorite = favorite,
    pairings = pairings.joinToString(SEP),
    drinkFrom = drinkFrom,
    drinkTo = drinkTo,
    drinkNow = drinkNow,
    tastingNotes = tastingNotes,
    source = source.name,
    addedLabel = addedLabel,
)
