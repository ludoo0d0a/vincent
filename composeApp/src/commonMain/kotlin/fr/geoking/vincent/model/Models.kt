package fr.geoking.vincent.model

import androidx.compose.ui.graphics.Color
import fr.geoking.vincent.theme.VincentColors
import org.jetbrains.compose.resources.StringResource
import vincent.composeapp.generated.resources.*

/** The four wine colours surfaced everywhere (filters, tags, rack caps). */
enum class WineColor(
    val label: StringResource,
    val glass: Color,
    val tagBg: Color,
    val tagFg: Color,
) {
    RED(Res.string.color_red, VincentColors.Red, Color(0xFFF4E2E1), Color(0xFF8E2A28)),
    WHITE(Res.string.color_white, VincentColors.WhiteWine, Color(0xFFF6EFDB), Color(0xFF8A6B1F)),
    ROSE(Res.string.color_rose, VincentColors.Rose, Color(0xFFF8E6EA), Color(0xFF9E4A5C)),
    SPARKLING(Res.string.color_sparkling, VincentColors.Bubbly, Color(0xFFF6F1DC), Color(0xFF7E7327));
}

/** Major wine regions / categories used for the rack "Catégorie" mode and search. */
enum class WineCategory(val short: String, val label: StringResource) {
    BORDEAUX("BX", Res.string.cat_bordeaux),
    BOURGOGNE("BG", Res.string.cat_bourgogne),
    RHONE("RH", Res.string.cat_rhone),
    PROVENCE("PR", Res.string.cat_provence),
    LOIRE("LO", Res.string.cat_loire),
    CHAMPAGNE("CH", Res.string.cat_champagne);
}

/** How a buyer acquired a bottle / why it is kept. */
enum class AddSource(val label: StringResource) {
    VOICE(Res.string.source_voice),
    SCAN(Res.string.source_scan),
    PHOTO(Res.string.source_photo),
    MANUAL(Res.string.source_manual);
}

/** Kind of photo attached to a bottle. */
enum class BottlePhotoKind(val label: String, val suffix: String) {
    BOTTLE("Bouteille", "bottle"),
    LABEL("Étiquette", "label"),
    BACK("Dos", "back"),
}

data class Bottle(
    val id: String,
    val domain: String,
    val appellation: String,
    val color: WineColor,
    val category: WineCategory,
    val vintage: String,            // e.g. "2016" or "NM"
    val price: Int,                 // unit price, in euros
    val quantity: Int,
    val rating: Double,             // /5
    val cellarSpot: String,         // e.g. "B3"
    val provenance: String,         // e.g. "Saint-Julien, FR"
    val merchant: String,           // caviste / magasin
    val purchaseDate: String,
    val occasion: String,
    val favorite: Boolean = false,
    val pairings: List<String> = emptyList(),
    val drinkFrom: Int = 0,
    val drinkTo: Int = 0,
    val drinkNow: Float = 0.5f,     // 0..1 position within the drink window
    val tastingNotes: String = "",
    val source: AddSource = AddSource.MANUAL,
    val addedLabel: String = "",    // e.g. "09:32" or "Lun."
    val photoBottle: String? = null,
    val photoLabel: String? = null,
    val photoBack: String? = null,
)

fun Bottle.photo(kind: BottlePhotoKind): String? = when (kind) {
    BottlePhotoKind.BOTTLE -> photoBottle
    BottlePhotoKind.LABEL -> photoLabel
    BottlePhotoKind.BACK -> photoBack
}

fun Bottle.withPhoto(kind: BottlePhotoKind, uri: String?): Bottle = when (kind) {
    BottlePhotoKind.BOTTLE -> copy(photoBottle = uri)
    BottlePhotoKind.LABEL -> copy(photoLabel = uri)
    BottlePhotoKind.BACK -> copy(photoBack = uri)
}

/** Best photo to show as a list thumbnail: étiquette → bouteille → dos. */
fun Bottle.thumbnailUri(): String? = photoLabel ?: photoBottle ?: photoBack

/** A single physical slot in a rack grid. */
data class RackCell(
    val row: String,
    val occupied: Boolean,
    val color: WineColor? = null,
    val category: WineCategory? = null,
    val vintage: String? = null,
    val price: Int? = null,
    val selected: Boolean = false,
)

/** What the rack overlay shows on each occupied cell. */
enum class RackMode(val label: StringResource) {
    COLOR(Res.string.cellar_mode_color),
    PRICE(Res.string.cellar_mode_price),
    VINTAGE(Res.string.cellar_mode_vintage),
    CATEGORY(Res.string.cellar_mode_category);
}

data class ColorBreakdown(val color: WineColor, val percent: Int)
