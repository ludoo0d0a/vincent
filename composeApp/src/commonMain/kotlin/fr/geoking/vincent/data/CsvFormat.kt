package fr.geoking.vincent.data

import fr.geoking.vincent.model.*

/**
 * CSV import/export for the cellar.
 *
 * Export uses Vincent's own column set (metadata only — photos stay on device).
 * Import is tolerant: it maps columns by header name across known aliases (FR/EN),
 * so files exported from Vincent, Vivino, PLOC and most spreadsheet exports load
 * without manual mapping. Photo columns in legacy files are ignored.
 */
object CsvFormat {

    private val COLUMNS = listOf(
        "id", "domain", "appellation", "color", "category", "vintage", "price",
        "quantity", "rating", "cellarSpot", "provenance", "merchant", "purchaseDate",
        "occasion", "favorite", "pairings", "drinkFrom", "drinkTo", "drinkNow",
        "agingPotential", "tastingNotes", "source", "addedLabel",
    )

    // Header aliases (lower-cased) used to locate a value across app formats.
    private val ALIASES = mapOf(
        "domain" to listOf("domain", "domaine", "winery", "producer", "producteur", "nom", "name", "nom du vin"),
        "appellation" to listOf("appellation", "wine name", "wine", "cuvée", "cuvee", "vin", "nom du vin"),
        "color" to listOf("color", "colour", "couleur", "wine type", "type"),
        "vintage" to listOf("vintage", "millésime", "millesime", "année", "annee", "year"),
        "price" to listOf("price", "prix", "prix d'achat", "purchase price", "price paid"),
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
        "drinkFrom" to listOf("drinkfrom", "apogée début", "drink from", "begin consume"),
        "drinkTo" to listOf("drinkto", "apogée fin", "drink to", "à boire avant", "end consume"),
        "drinkNow" to listOf("drinknow"),
        "agingPotential" to listOf("agingpotential", "potentiel de garde", "aging potential"),
        "tastingNotes" to listOf("tastingnotes", "notes", "tasting notes", "commentaire"),
        "source" to listOf("source"),
        "addedLabel" to listOf("addedlabel"),

        // Tastings
        "wineName" to listOf("winename", "vin", "bouteille", "nom du vin"),
        "date" to listOf("date", "date dégustation", "tasting date"),
        "notes" to listOf("notes", "commentaire", "tasting notes"),

        // Producers / Suppliers
        "name" to listOf("name", "nom", "raison sociale"),
        "region" to listOf("region", "région"),
        "country" to listOf("country", "pays"),
        "website" to listOf("website", "site web", "url"),
        "email" to listOf("email", "e-mail", "courriel"),
        "phone" to listOf("phone", "téléphone", "tel"),
        "type" to listOf("type"),

        // Racks
        "cols" to listOf("cols", "colonnes", "nombre de colonnes", "colonne"),
        "rows" to listOf("rows", "rangées", "nombre de lignes", "ligne"),
        "staggered" to listOf("staggered", "quinconce"),
        "staggerOffset" to listOf("staggeroffset", "décalage", "decalage"),
        "format" to listOf("format", "type de casier"),
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
                b.drinkNow.toString(), b.agingPotential.toString(), b.tastingNotes,
                b.source.name, b.addedLabel,
            )
            appendLine(row.joinToString(",") { esc(it) })
        }
    }

    fun racksToCsv(racks: List<Rack>): String = buildString {
        appendLine(listOf("name", "cols", "rows", "staggered", "staggerOffset", "format").joinToString(",") { esc(it) })
        racks.forEach { r ->
            val row = listOf(
                r.name, r.cols.toString(), r.rows.toString(), r.staggered.toString(),
                r.staggerOffset.toString(), r.format.name
            )
            appendLine(row.joinToString(",") { esc(it) })
        }
    }

    fun tastingsToCsv(tastings: List<Tasting>): String = buildString {
        appendLine(listOf("wineName", "date", "rating", "notes", "color", "vintage").joinToString(",") { esc(it) })
        tastings.forEach { t ->
            val row = listOf(
                t.wineName, t.date, t.rating.toString(), t.notes, t.color?.name ?: "", t.vintage ?: ""
            )
            appendLine(row.joinToString(",") { esc(it) })
        }
    }

    fun producersToCsv(producers: List<Producer>): String = buildString {
        appendLine(listOf("name", "region", "country", "website", "email", "phone").joinToString(",") { esc(it) })
        producers.forEach { p ->
            val row = listOf(p.name, p.region, p.country, p.website, p.email, p.phone)
            appendLine(row.joinToString(",") { esc(it) })
        }
    }

    fun suppliersToCsv(suppliers: List<Supplier>): String = buildString {
        appendLine(listOf("name", "type", "website", "email", "phone").joinToString(",") { esc(it) })
        suppliers.forEach { s ->
            val row = listOf(s.name, s.type, s.website, s.email, s.phone)
            appendLine(row.joinToString(",") { esc(it) })
        }
    }

    private fun esc(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    // ---------- import ----------

    enum class ImportType { BOTTLES, RACKS, TASTINGS, PRODUCERS, SUPPLIERS, UNKNOWN }
    data class ImportResult(
        val type: ImportType,
        val bottles: List<Bottle> = emptyList(),
        val racks: List<Rack> = emptyList(),
        val tastings: List<Tasting> = emptyList(),
        val producers: List<Producer> = emptyList(),
        val suppliers: List<Supplier> = emptyList(),
        val source: String,
    )

    fun parse(text: String): ImportResult {
        val rows = tokenize(text)
        if (rows.isEmpty()) return ImportResult(ImportType.UNKNOWN, source = "Inconnu")
        val header = rows.first().map { it.trim().lowercase() }
        val index = HashMap<String, Int>()
        header.forEachIndexed { i, h -> index.putIfAbsent(h, i) }

        val source = detectSource(header)
        val type = detectType(header)

        return when (type) {
            ImportType.BOTTLES -> ImportResult(type, bottles = rows.drop(1).mapNotNull { it.toBottle(index) }, source = source)
            ImportType.RACKS -> ImportResult(type, racks = rows.drop(1).mapNotNull { it.toRack(index) }, source = source)
            ImportType.TASTINGS -> ImportResult(type, tastings = rows.drop(1).mapNotNull { it.toTasting(index) }, source = source)
            ImportType.PRODUCERS -> ImportResult(type, producers = rows.drop(1).mapNotNull { it.toProducer(index) }, source = source)
            ImportType.SUPPLIERS -> ImportResult(type, suppliers = rows.drop(1).mapNotNull { it.toSupplier(index) }, source = source)
            else -> ImportResult(ImportType.UNKNOWN, source = source)
        }
    }

    fun detectSource(header: List<String>): String = when {
        "id" in header && "domain" in header && "color" in header -> "Vincent"
        header.any { it == "winery" } || header.any { it == "wine name" } -> "Vivino"
        ("nom" in header || "nom du vin" in header) && (header.any { it == "couleur" } || header.any { it == "millésime" } || header.any { it == "date dégustation" }) -> "PLOC"
        header.any { it == "bin" } || header.any { it == "begin consume" } || header.any { it == "valuation" } -> "CellarTracker"
        else -> "CSV générique"
    }

    fun detectType(header: List<String>): ImportType = when {
        header.any { it in ALIASES["domain"]!! } && header.any { it in ALIASES["vintage"]!! } -> ImportType.BOTTLES
        header.any { it in ALIASES["cols"]!! } || (header.any { it == "nom" } && header.size <= 5) -> ImportType.RACKS
        header.any { it in ALIASES["date"]!! } && header.any { it in ALIASES["notes"]!! } -> ImportType.TASTINGS
        header.any { it == "région" } -> ImportType.PRODUCERS
        header.any { it == "type" } && header.any { it == "nom" } -> ImportType.SUPPLIERS
        else -> ImportType.BOTTLES // Default to bottles if ambiguous
    }

    private fun List<String>.field(key: String, index: Map<String, Int>): String? {
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

    private fun List<String>.toBottle(index: Map<String, Int>): Bottle? {
        val domain = field("domain", index) ?: field("appellation", index) ?: return null
        val appellation = field("appellation", index) ?: field("provenance", index) ?: ""
        val color = parseColor(field("color", index))
        val provenance = field("provenance", index) ?: appellation
        val vintage = field("vintage", index) ?: "NM"
        val id = field("id", index) ?: slug("$domain-$vintage")
        val category = field("category", index)?.let { runCatching { WineCategory.valueOf(it) }.getOrNull() }
            ?: guessCategory(provenance + " " + appellation)

        return Bottle(
            id = id,
            domain = domain,
            appellation = appellation.ifEmpty { provenance },
            color = color,
            category = category,
            vintage = vintage,
            price = field("price", index).toIntOr(0),
            quantity = field("quantity", index).toIntOr(1),
            rating = field("rating", index).toDoubleOr(0.0).let { if (it > 5) it / 20.0 else it },
            cellarSpot = field("cellarSpot", index) ?: "—",
            provenance = provenance,
            merchant = field("merchant", index) ?: "—",
            purchaseDate = field("purchaseDate", index) ?: "—",
            occasion = field("occasion", index) ?: "—",
            favorite = field("favorite", index).toBoolLoose(),
            pairings = field("pairings", index)?.split(";", ",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            drinkFrom = field("drinkFrom", index).toIntOr(0),
            drinkTo = field("drinkTo", index).toIntOr(0),
            drinkNow = field("drinkNow", index)?.toFloatOrNull() ?: 0.5f,
            agingPotential = field("agingPotential", index).toIntOr(0),
            tastingNotes = field("tastingNotes", index) ?: "",
            source = field("source", index)?.let { runCatching { AddSource.valueOf(it) }.getOrNull() } ?: AddSource.MANUAL,
            addedLabel = field("addedLabel", index) ?: "importé",
        )
    }

    private fun List<String>.toRack(index: Map<String, Int>): Rack? {
        val name = field("name", index) ?: return null
        var cols = field("cols", index).toIntOr(6)
        var rows = field("rows", index).toIntOr(6)
        val staggered = field("staggered", index).toBoolLoose()
        val staggerOffset = field("staggerOffset", index).toBoolLoose()
        val format = field("format", index)?.let { runCatching { RackFormat.valueOf(it.uppercase()) }.getOrNull() }
            ?: RackFormat.GRID
        // X-bins group 2×2 cells, so the grid must be even in both dimensions.
        if (format == RackFormat.X) {
            if (cols % 2 != 0) cols += 1
            if (rows % 2 != 0) rows += 1
        }
        return emptyRack(name, cols, rows, staggered).copy(format = format, staggerOffset = staggerOffset)
    }

    private fun List<String>.toTasting(index: Map<String, Int>): Tasting? {
        val wineName = field("wineName", index) ?: field("domain", index) ?: return null
        val date = field("date", index) ?: "—"
        val rating = field("rating", index).toDoubleOr(0.0)
        val notes = field("notes", index) ?: ""
        return Tasting(
            id = slug("$wineName-$date"),
            wineName = wineName,
            date = date,
            rating = rating,
            notes = notes,
            color = parseColor(field("color", index)),
            vintage = field("vintage", index),
        )
    }

    private fun List<String>.toProducer(index: Map<String, Int>): Producer? {
        val name = field("name", index) ?: return null
        return Producer(
            id = slug(name),
            name = name,
            region = field("region", index) ?: "",
            country = field("country", index) ?: "",
            website = field("website", index) ?: "",
            email = field("email", index) ?: "",
            phone = field("phone", index) ?: "",
        )
    }

    private fun List<String>.toSupplier(index: Map<String, Int>): Supplier? {
        val name = field("name", index) ?: return null
        return Supplier(
            id = slug(name),
            name = name,
            type = field("type", index) ?: "",
            website = field("website", index) ?: "",
            email = field("email", index) ?: "",
            phone = field("phone", index) ?: "",
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
        val firstLine = s.substringBefore('\n')
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

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
                c == separator -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field = StringBuilder() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { cells -> cells.any { it.isNotBlank() } }
    }
}
