package fr.geoking.vincent.data

import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor

/** Minimal wine identity extracted from a PLOC caves / dégustations export. */
data class PlocWineRef(
    val wineName: String,
    val vintage: String? = null,
    val plocId: String? = null,
    val color: WineColor? = null,
) {
    val key: String
        get() = plocId?.takeIf { it.isNotBlank() }
            ?: "${wineName.trim().lowercase()}|${vintage?.trim()?.ifBlank { null } ?: "NM"}"

    fun resolvedId(): String =
        plocId?.takeIf { it.isNotBlank() } ?: plocSlug("$wineName-${vintage ?: "NM"}")
}

/** A CSV file picked for a PLOC bundle import (name used for type detection). */
data class PlocCsvFile(val name: String, val text: String)

data class PlocBundleResult(
    val bottles: Int = 0,
    val racks: Int = 0,
    val tastings: Int = 0,
    val producers: Int = 0,
    val suppliers: Int = 0,
    val bottlesFromRefs: Int = 0,
    val filesImported: Int = 0,
    val filesSkipped: Int = 0,
) {
    val isEmpty: Boolean get() = filesImported == 0
}

object PlocImport {

    /** Import every recognised PLOC export file from a multi-file pick (merge-safe). */
    fun applyBundle(files: List<PlocCsvFile>): PlocBundleResult {
        if (files.isEmpty()) return PlocBundleResult()

        val recognised = files.mapNotNull { file ->
            val kind = PlocKnownFile.fromFilename(file.name) ?: return@mapNotNull null
            kind to file
        }.sortedBy { it.first.order }

        var bottles = 0
        var racks = 0
        var tastings = 0
        var producers = 0
        var suppliers = 0
        var bottlesFromRefs = 0
        var imported = 0

        for ((kind, file) in recognised) {
            when (kind) {
                PlocKnownFile.VINS -> {
                    val result = CsvFormat.parse(file.text)
                    if (result.type == CsvFormat.ImportType.BOTTLES) {
                        bottles += Cellar.importBottles(result.bottles)
                        imported++
                    }
                }
                PlocKnownFile.PRODUCTEURS -> {
                    val result = CsvFormat.parse(file.text, CsvFormat.ImportType.PRODUCERS)
                    if (result.type == CsvFormat.ImportType.PRODUCERS) {
                        producers += Producers.import(result.producers)
                        imported++
                    }
                }
                PlocKnownFile.FOURNISSEURS -> {
                    val result = CsvFormat.parse(file.text, CsvFormat.ImportType.SUPPLIERS)
                    if (result.type == CsvFormat.ImportType.SUPPLIERS) {
                        suppliers += Suppliers.import(result.suppliers)
                        imported++
                    }
                }
                PlocKnownFile.CAVES -> {
                    val result = CsvFormat.parse(file.text)
                    if (result.type == CsvFormat.ImportType.RACKS) {
                        bottlesFromRefs += ensureBottlesFromRacks(result.referencedWines)
                        racks += Racks.import(result.racks)
                        imported++
                    }
                }
                PlocKnownFile.DEGUSTATIONS -> {
                    val result = CsvFormat.parse(file.text)
                    if (result.type == CsvFormat.ImportType.TASTINGS) {
                        bottlesFromRefs += ensureBottlesFromTastings(result.tastings)
                        tastings += Tastings.import(result.tastings)
                        imported++
                    }
                }
            }
        }

        return PlocBundleResult(
            bottles = bottles,
            racks = racks,
            tastings = tastings,
            producers = producers,
            suppliers = suppliers,
            bottlesFromRefs = bottlesFromRefs,
            filesImported = imported,
            filesSkipped = files.size - imported,
        )
    }

    /** Creates cellar bottles that are referenced but not yet present (merge-safe). */
    fun ensureBottles(refs: List<PlocWineRef>, quantity: Int = 0): Int {
        if (refs.isEmpty()) return 0
        val existingIds = Cellar.bottles.map { it.id }.toSet()
        val existingKeys = Cellar.bottles
            .map { "${it.domain.trim().lowercase()}|${it.vintage}" }
            .toSet()
        val toCreate = refs
            .filter { it.wineName.isNotBlank() }
            .distinctBy { it.key }
            .filter { ref ->
                ref.resolvedId() !in existingIds &&
                    "${ref.wineName.trim().lowercase()}|${ref.vintage?.trim()?.ifBlank { "NM" } ?: "NM"}" !in existingKeys
            }
            .map { it.toBottle(quantity) }
        return Cellar.importBottles(toCreate)
    }

    fun ensureBottlesFromTastings(tastings: List<Tasting>): Int =
        ensureBottles(
            tastings.map { PlocWineRef(it.wineName, it.vintage, color = it.color) },
            quantity = 0,
        )

    fun ensureBottlesFromRacks(refs: List<PlocWineRef>): Int =
        ensureBottles(refs, quantity = 1)
}

private enum class PlocKnownFile(val order: Int) {
    VINS(0),
    PRODUCTEURS(1),
    FOURNISSEURS(2),
    CAVES(3),
    DEGUSTATIONS(4),
    ;

    companion object {
        fun fromFilename(name: String): PlocKnownFile? {
            val base = name.substringAfterLast('/').substringAfterLast('\\').lowercase()
            return when {
                (base.contains("vin") || base == "wines.csv") && !base.contains("achat") && !base.contains("conso") -> VINS
                base.contains("cave") || base.contains("casier") || base.contains("rack") -> CAVES
                base.contains("degust") || base.contains("dégust") || base.contains("tasting") -> DEGUSTATIONS
                base.contains("producteur") || base.contains("producer") -> PRODUCTEURS
                base.contains("fournisseur") || base.contains("supplier") -> FOURNISSEURS
                else -> null
            }
        }
    }
}

private fun PlocWineRef.toBottle(quantity: Int): Bottle {
    val v = vintage?.trim()?.takeIf { it.isNotBlank() } ?: "NM"
    val name = wineName.trim()
    return Bottle(
        id = resolvedId(),
        domain = name,
        appellation = name,
        color = color ?: WineColor.RED,
        category = guessPlocCategory(name),
        vintage = v,
        price = 0,
        quantity = quantity,
        rating = 0.0,
        cellarSpot = "—",
        provenance = "—",
        merchant = "—",
        purchaseDate = "—",
        occasion = "—",
        source = AddSource.MANUAL,
        addedLabel = "import PLOC",
    )
}

private fun guessPlocCategory(text: String): WineCategory {
    val v = text.lowercase()
    return when {
        "bourgogne" in v || "burgundy" in v || "chablis" in v -> WineCategory.BOURGOGNE
        "rhône" in v || "rhone" in v || "gigondas" in v -> WineCategory.RHONE
        "provence" in v || "bandol" in v -> WineCategory.PROVENCE
        "loire" in v || "sancerre" in v || "anjou" in v -> WineCategory.LOIRE
        "champagne" in v || "brut" in v -> WineCategory.CHAMPAGNE
        else -> WineCategory.BORDEAUX
    }
}

internal fun plocSlug(s: String): String =
    s.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')

/** PLOC grid index: numeric (1-based) or letter column (A=1). */
internal fun plocGridIndex(raw: String?): Int {
    val v = raw?.trim()?.uppercase() ?: return 0
    v.toIntOrNull()?.let { return (it - 1).coerceAtLeast(0) }
    if (v.length == 1 && v[0] in 'A'..'Z') return v[0] - 'A'
    return 0
}
