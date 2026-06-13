package com.geoking.vincent.model

import androidx.compose.ui.graphics.Color
import com.geoking.vincent.theme.VincentColors

/** The four wine colours surfaced everywhere (filters, tags, rack caps). */
enum class WineColor(
    val label: String,
    val glass: Color,
    val tagBg: Color,
    val tagFg: Color,
) {
    RED("Rouge", VincentColors.Red, Color(0xFFF4E2E1), Color(0xFF8E2A28)),
    WHITE("Blanc", VincentColors.WhiteWine, Color(0xFFF6EFDB), Color(0xFF8A6B1F)),
    ROSE("Rosé", VincentColors.Rose, Color(0xFFF8E6EA), Color(0xFF9E4A5C)),
    SPARKLING("Pétillant", VincentColors.Bubbly, Color(0xFFF6F1DC), Color(0xFF7E7327));
}

/** Major wine regions / categories used for the rack "Catégorie" mode and search. */
enum class WineCategory(val short: String, val label: String) {
    BORDEAUX("BX", "Bordeaux"),
    BOURGOGNE("BG", "Bourgogne"),
    RHONE("RH", "Rhône"),
    PROVENCE("PR", "Provence"),
    LOIRE("LO", "Loire"),
    CHAMPAGNE("CH", "Champagne");
}

/** How a buyer acquired a bottle / why it is kept. */
enum class AddSource(val label: String) {
    VOICE("Ajouté par la voix"),
    SCAN("Scan étiquette"),
    PHOTO("Photo"),
    MANUAL("Saisie manuelle");
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
)

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
enum class RackMode(val label: String) {
    COLOR("Couleur"),
    PRICE("Prix"),
    VINTAGE("Millésime"),
    CATEGORY("Catégorie");
}

data class ColorBreakdown(val color: WineColor, val percent: Int)
