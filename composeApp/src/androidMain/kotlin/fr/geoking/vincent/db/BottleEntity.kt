package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.FlavorProfile
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
    val description: String = "",
    val pairingNotes: String = "",
    val grapes: String = "",
    val flavorProfile: String = "",
    val maturity: String = "",
    val source: String,
    val addedLabel: String,
    /** Legacy column name — stores the front-label photo path or URL. */
    val imageUri: String = "",
    val photoBottleUri: String = "",
    val photoBackUri: String = "",
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
    description = description,
    pairingNotes = pairingNotes,
    grapes = if (grapes.isEmpty()) emptyList() else grapes.split(SEP),
    flavorProfile = decodeFlavor(flavorProfile),
    maturity = maturity,
    source = AddSource.valueOf(source),
    addedLabel = addedLabel,
    photoBottle = photoBottleUri.takeIf { it.isNotBlank() },
    photoLabel = imageUri.takeIf { it.isNotBlank() },
    photoBack = photoBackUri.takeIf { it.isNotBlank() },
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
    description = description,
    pairingNotes = pairingNotes,
    grapes = grapes.joinToString(SEP),
    flavorProfile = encodeFlavor(flavorProfile),
    maturity = maturity,
    source = source.name,
    addedLabel = addedLabel,
    imageUri = photoLabel.orEmpty(),
    photoBottleUri = photoBottle.orEmpty(),
    photoBackUri = photoBack.orEmpty(),
)

/** FlavorProfile ↔ a compact "s,a,t,al,b,f" string (empty when absent). */
private fun encodeFlavor(f: FlavorProfile?): String =
    if (f == null) "" else "${f.sweetness},${f.acidity},${f.tannins},${f.alcohol},${f.body},${f.finish}"

private fun decodeFlavor(s: String): FlavorProfile? {
    if (s.isBlank()) return null
    val p = s.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (p.size == 6) FlavorProfile(p[0], p[1], p[2], p[3], p[4], p[5]) else null
}
