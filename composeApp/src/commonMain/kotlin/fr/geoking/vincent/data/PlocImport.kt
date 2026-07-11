package fr.geoking.vincent.data

import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor

/** Minimal wine identity extracted from a PLOC caves / d�gustations export. */
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

object PlocImport {

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
        cellarSpot = "�",
        provenance = "�",
        merchant = "�",
        purchaseDate = "�",
        occasion = "�",
        source = AddSource.MANUAL,
        addedLabel = "import PLOC",
    )
}

private fun guessPlocCategory(text: String): WineCategory {
    val v = text.lowercase()
    return when {
        "bourgogne" in v || "burgundy" in v || "chablis" in v -> WineCategory.BOURGOGNE
        "rh�ne" in v || "rhone" in v || "gigondas" in v -> WineCategory.RHONE
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
