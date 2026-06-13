package com.vincent.data

import com.vincent.model.AddSource
import com.vincent.model.Bottle
import com.vincent.model.WineCategory
import com.vincent.model.WineColor

/**
 * CSV import/export for the cellar.
 *
 * Export uses Vincent's own column set (full round-trip). Import is tolerant: it
 * maps columns by header name across known aliases (FR/EN), so files exported from
 * Vincent, Vivino, PLOC and most spreadsheet exports load without manual mapping.
 */
object CsvFormat {

    private val COLUMNS = listOf(
        "id", "domain", "appellation", "color", "category", "vintage", "price",
        "quantity", "rating", "cellarSpot", "provenance", "merchant", "purchaseDate",
        "occasion", "favorite", "pairings", "drinkFrom", "drinkTo", "drinkNow",
        "tastingNotes", "source", "addedLabel",
    )

    // Header aliases (lower-cased) used to locate a value across app formats.
    private val ALIASES = mapOf(
        "domain" to listOf("domain", "domaine", "winery", "producer", "producteur", "nom", "name"),
        "appellation" to listOf("appellation", "wine name", "wine", "cuvée", "cuvee", "vin"),
        "color" to listOf("color", "colour", "couleur", "wine type", "type"),
        "vintage" to listOf("vintage", "millésime", "millesime", "année", "annee", "year"),
        "price" to listOf("price", "prix", "prix d'achat", "purchase price"),
        "quantity" to listOf("quantity", "quantité", "quantite", "qty", "stock", "bottles", "nombre"),
        "rating" to listOf("rating", "note", "your rating", "score", "ma note"),
        "cellarSpot" to listOf("cellarspot", "casier", "emplacement", "location", "bin", "rangement"),
        "provenance" to listOf("provenance", "region", "région", "country", "pays", "origine"),
        "merchant" to listOf("merchant", "caviste", "magasin", "store", "vendor", "acheté chez"),
        "purchaseDate" to listOf("purchasedate", "purchase date", "date", "date d'achat", "scan date"),
        "occasion" to listOf("occasion", "usage"),
        "id" to listOf("id"),
        "category" to listOf("category", "catégorie", "categorie"),
        "favorite" to listOf("favorite", "favori", "favourite"),
        "pairings" to listOf("pairings", "accords", "accords mets-vin"),
        "drinkFrom" to listOf("drinkfrom", "apogée début", "drink from"),
        "drinkTo" to listOf("drinkto", "apogée fin", "drink to", "à boire avant"),
        "drinkNow" to listOf("drinknow"),
        "tastingNotes" to listOf("tastingnotes", "notes", "tasting notes", "commentaire"),
        "source" to listOf("source"),
        "addedLabel" to listOf("addedlabel"),
    )

    // ---------- export ----------

    fun toCsv(bottles: List<Bottle>): String = buildString {
        appendLine(COLUMNS.joinToString(",") { esc(it) })
        bottles.forEach { b ->
            val row = listOf(
                b.id, b.domain, b.appellation, b.color.name, b.category.name, b.vintage,
                b.price.toString(), b.quantity.toString(), b.rating.toString(), b.cellarSpot,
                b.provenance, b.merchant, b.purchaseDate, b.occasion, b.favorite.toString(),
                b.pairings.joinToString(";"), b.drinkFrom.toString(), b.drinkTo.toString(),
                b.drinkNow.toString(), b.tastingNotes, b.source.name, b.addedLabel,
            )
            appendLine(row.joinToString(",") { esc(it) })
        }
    }

    private fun esc(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    // ---------- import ----------

    data class ImportResult(val bottles: List<Bottle>, val source: String)

    fun parse(text: String): ImportResult {
        val rows = tokenize(text)
        if (rows.isEmpty()) return ImportResult(emptyList(), "Inconnu")
        val header = rows.first().map { it.trim().lowercase() }
        val index = HashMap<String, Int>()
        header.forEachIndexed { i, h -> index.putIfAbsent(h, i) }

        val source = detectSource(header)
        val bottles = rows.drop(1).mapNotNull { row -> row.toBottle(index) }
        return ImportResult(bottles, source)
    }

    fun detectSource(header: List<String>): String = when {
        "id" in header && "domain" in header && "color" in header -> "Vincent"
        header.any { it == "winery" } || header.any { it == "wine name" } -> "Vivino"
        "nom" in header && (header.any { it == "couleur" } || header.any { it == "millésime" }) -> "PLOC"
        else -> "CSV générique"
    }

    private fun List<String>.toBottle(index: Map<String, Int>): Bottle? {
        fun field(key: String): String? {
            val aliases = ALIASES[key] ?: return null
            for (a in aliases) {
                val i = index[a] ?: continue
                if (i < size) {
                    val v = this[i].trim()
                    if (v.isNotEmpty()) return v
                }
            }
            return null
        }

        val domain = field("domain") ?: field("appellation") ?: return null
        val appellation = field("appellation") ?: field("provenance") ?: ""
        val color = parseColor(field("color"))
        val provenance = field("provenance") ?: appellation
        val vintage = field("vintage") ?: "NM"
        val id = field("id") ?: slug("$domain-$vintage")
        val category = field("category")?.let { runCatching { WineCategory.valueOf(it) }.getOrNull() }
            ?: guessCategory(provenance + " " + appellation)

        return Bottle(
            id = id,
            domain = domain,
            appellation = appellation.ifEmpty { provenance },
            color = color,
            category = category,
            vintage = vintage,
            price = field("price").toIntOr(0),
            quantity = field("quantity").toIntOr(1),
            rating = field("rating").toDoubleOr(0.0).let { if (it > 5) it / 20.0 else it }, // Vivino sometimes /100
            cellarSpot = field("cellarSpot") ?: "—",
            provenance = provenance,
            merchant = field("merchant") ?: "—",
            purchaseDate = field("purchaseDate") ?: "—",
            occasion = field("occasion") ?: "—",
            favorite = field("favorite").toBoolLoose(),
            pairings = field("pairings")?.split(";", ",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            drinkFrom = field("drinkFrom").toIntOr(0),
            drinkTo = field("drinkTo").toIntOr(0),
            drinkNow = field("drinkNow")?.toFloatOrNull() ?: 0.5f,
            tastingNotes = field("tastingNotes") ?: "",
            source = field("source")?.let { runCatching { AddSource.valueOf(it) }.getOrNull() } ?: AddSource.MANUAL,
            addedLabel = field("addedLabel") ?: "importé",
        )
    }

    // ---------- helpers ----------

    private fun parseColor(raw: String?): WineColor {
        val v = raw?.lowercase() ?: return WineColor.RED
        return when {
            "ros" in v -> WineColor.ROSE
            "blanc" in v || "white" in v -> WineColor.WHITE
            "pétill" in v || "petill" in v || "spark" in v || "champ" in v || "efferv" in v || "mousseux" in v -> WineColor.SPARKLING
            else -> WineColor.RED
        }
    }

    private fun guessCategory(text: String): WineCategory {
        val v = text.lowercase()
        return when {
            "bourgogne" in v || "burgundy" in v || "chablis" in v -> WineCategory.BOURGOGNE
            "rhône" in v || "rhone" in v -> WineCategory.RHONE
            "provence" in v || "bandol" in v -> WineCategory.PROVENCE
            "loire" in v || "sancerre" in v -> WineCategory.LOIRE
            "champagne" in v || "reims" in v -> WineCategory.CHAMPAGNE
            else -> WineCategory.BORDEAUX
        }
    }

    private fun slug(s: String): String =
        s.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')

    private fun String?.toIntOr(d: Int): Int =
        this?.filter { it.isDigit() }?.toIntOrNull() ?: d

    private fun String?.toDoubleOr(d: Double): Double =
        this?.replace(',', '.')?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull() ?: d

    private fun String?.toBoolLoose(): Boolean =
        this?.lowercase() in setOf("true", "1", "oui", "yes", "y")

    /** Quote-aware CSV tokenizer; normalises CRLF and drops fully-blank rows. */
    private fun tokenize(text: String): List<List<String>> {
        val s = text.replace("\r\n", "\n").replace("\r", "\n")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field = StringBuilder() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { cells -> cells.any { it.isNotBlank() } }
    }
}
