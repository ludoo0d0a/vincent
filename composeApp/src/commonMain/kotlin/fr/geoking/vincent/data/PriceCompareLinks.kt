package fr.geoking.vincent.data

import fr.geoking.vincent.model.Bottle

/** External search link — opens in the browser; Vincent does not fetch or store prices. */
data class PriceCompareLink(val label: String, val url: String)

/** Builds retailer / comparator search URLs from bottle metadata. */
fun bottlePriceCompareLinks(bottle: Bottle): List<PriceCompareLink> {
    val q = buildList {
        add(bottle.domain.trim())
        if (bottle.vintage.isNotBlank() && bottle.vintage != "NM") add(bottle.vintage.trim())
        add(bottle.appellation.trim())
    }.filter { it.isNotEmpty() }.joinToString(" ")
    if (q.isBlank()) return emptyList()
    val encoded = q.urlEncode()
    val plus = q.replace(" ", "+")
    return listOf(
        PriceCompareLink("Nicolas", "https://www.nicolas.com/fr/search?text=$plus"),
        PriceCompareLink("Vinatis", "https://www.vinatis.com/?s=$plus"),
        PriceCompareLink("Amazon", "https://www.amazon.fr/s?k=$plus+vin"),
        PriceCompareLink("Lavinia", "https://www.lavinia.com/fr-fr/catalogsearch/result/?q=$plus"),
        PriceCompareLink("Vivino", "https://www.vivino.com/search/wines?q=$encoded"),
        PriceCompareLink("Wine-Searcher", "https://www.wine-searcher.com/find/$plus"),
        PriceCompareLink("Google", "https://www.google.com/search?q=$encoded+prix+vin"),
    )
}

private fun String.urlEncode(): String = encodeToByteArray().joinToString("") { b ->
    val c = b.toInt() and 0xFF
    when (c) {
        in 'A'.code..'Z'.code, in 'a'.code..'z'.code, in '0'.code..'9'.code -> c.toChar().toString()
        '-'.code, '_'.code, '.'.code, '~'.code -> c.toChar().toString()
        else -> "%${c.toString(16).uppercase().padStart(2, '0')}"
    }
}
