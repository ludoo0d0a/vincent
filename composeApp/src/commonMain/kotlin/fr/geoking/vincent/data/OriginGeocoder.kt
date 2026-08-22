package fr.geoking.vincent.data

import fr.geoking.vincent.model.Appellation
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class OriginKind {
    Appellation,
    MacroRegion,
    Country,
    Unmapped,
}

data class ResolvedOrigin(
    val key: String,
    val label: String,
    val kind: OriginKind,
    val geoAsset: String? = null,
    val macroRegionKey: String? = null,
    val latLon: Pair<Double, Double>? = null,
)

@Serializable
private data class CentroidPoint(val lat: Double, val lon: Double, val label: String = "")

@Serializable
private data class OriginCentroidsFile(
    val macroRegions: Map<String, CentroidPoint> = emptyMap(),
    val countries: Map<String, CentroidPoint> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
)

private val centroidsJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val categoryMacroKeys = mapOf(
    WineCategory.BORDEAUX to "bordeaux",
    WineCategory.BOURGOGNE to "bourgogne",
    WineCategory.RHONE to "rhone",
    WineCategory.PROVENCE to "provence",
    WineCategory.LOIRE to "loire",
    WineCategory.CHAMPAGNE to "champagne",
)

object OriginGeocoder {
    private var centroids: OriginCentroidsFile? = null

    fun loadCentroids(json: String) {
        centroids = runCatching { centroidsJson.decodeFromString<OriginCentroidsFile>(json) }.getOrNull()
    }

    fun resolveOrigin(bottle: Bottle): ResolvedOrigin {
        matchAppellation(bottle.appellation)?.let { return it }
        matchAppellation(bottle.provenance)?.let { return it }
        matchCountry(bottle.provenance)?.let { return it }
        matchMacroRegion(bottle)?.let { return it }
        return unmapped(bottle)
    }

    private fun matchAppellation(text: String): ResolvedOrigin? {
        val q = text.trim().lowercase()
        if (q.length < 2) return null
        val app = Appellations.all.firstOrNull { app ->
            val name = app.name.lowercase()
            name == q || q.contains(name) || name.contains(q) ||
                (app.sign.isNotBlank() && q.contains(app.sign.lowercase()))
        } ?: return null
        return ResolvedOrigin(
            key = "app:${app.id}",
            label = app.name,
            kind = OriginKind.Appellation,
            geoAsset = app.geoAsset.takeIf { it.isNotBlank() },
            macroRegionKey = inferMacroFromDepartment(app.department),
        )
    }

    private fun inferMacroFromDepartment(dept: String): String? {
        val code = dept.trim().take(2).toIntOrNull() ?: return null
        return when (code) {
            in 16..17, 24, 33, 40, 47, 64 -> "bordeaux"
            in 21..39, 58, 71, 89 -> "bourgogne"
            in 7..8, 26, 30, 38, 42, 69, 73, 74 -> "rhone"
            in 4..6, 13, 83, 84 -> "provence"
            in 41..49, 72 -> "loire"
            in 8..10, 51, 52 -> "champagne"
            else -> null
        }
    }

    private fun matchMacroRegion(bottle: Bottle): ResolvedOrigin? {
        val c = centroids ?: return categoryOnly(bottle.category)
        val provenance = bottle.provenance.trim().lowercase()
        if (provenance.isNotBlank()) {
            resolveAlias(provenance, c)?.let { key ->
                c.macroRegions[key]?.let { pt ->
                    return macroOrigin(key, pt)
                }
            }
            for ((alias, target) in c.aliases) {
                if (provenance.contains(alias) && c.macroRegions.containsKey(target)) {
                    return macroOrigin(target, c.macroRegions.getValue(target))
                }
            }
        }
        if (provenance.isBlank() && bottle.appellation.isBlank()) return null
        return categoryOnly(bottle.category)
    }

    private fun categoryOnly(category: WineCategory): ResolvedOrigin? {
        val key = categoryMacroKeys[category] ?: return null
        val c = centroids ?: return ResolvedOrigin(
            key = "macro:$key",
            label = category.name.lowercase().replaceFirstChar { it.uppercase() },
            kind = OriginKind.MacroRegion,
            macroRegionKey = key,
        )
        val pt = c.macroRegions[key] ?: return null
        return macroOrigin(key, pt)
    }

    private fun macroOrigin(key: String, pt: CentroidPoint): ResolvedOrigin = ResolvedOrigin(
        key = "macro:$key",
        label = pt.label.ifBlank { key.replaceFirstChar { it.uppercase() } },
        kind = OriginKind.MacroRegion,
        macroRegionKey = key,
        latLon = pt.lat to pt.lon,
    )

    private fun matchCountry(provenance: String): ResolvedOrigin? {
        val c = centroids ?: return null
        val text = provenance.trim().lowercase()
        if (text.isBlank()) return null

        val trailing = text.substringAfterLast(',').trim()
        resolveAlias(trailing, c)?.let { iso ->
            c.countries[iso]?.let { pt ->
                return countryOrigin(iso, pt)
            }
        }
        resolveAlias(text, c)?.let { iso ->
            c.countries[iso]?.let { pt ->
                return countryOrigin(iso, pt)
            }
        }
        for ((alias, target) in c.aliases) {
            if (c.countries.containsKey(target) && text.contains(alias)) {
                return countryOrigin(target, c.countries.getValue(target))
            }
        }
        return null
    }

    private fun resolveAlias(text: String, c: OriginCentroidsFile): String? {
        val key = text.trim().lowercase()
        if (key.length == 2 && c.countries.containsKey(key.uppercase())) return key.uppercase()
        return c.aliases[key] ?: c.countries.keys.firstOrNull { it.equals(key, ignoreCase = true) }
    }

    private fun countryOrigin(iso: String, pt: CentroidPoint): ResolvedOrigin = ResolvedOrigin(
        key = "country:$iso",
        label = pt.label.ifBlank { iso },
        kind = OriginKind.Country,
        latLon = pt.lat to pt.lon,
    )

    private fun unmapped(bottle: Bottle): ResolvedOrigin {
        val label = listOf(bottle.provenance, bottle.appellation, bottle.domain)
            .firstOrNull { it.isNotBlank() } ?: "ù"
        return ResolvedOrigin(
            key = "unmapped:${label.lowercase()}",
            label = label,
            kind = OriginKind.Unmapped,
        )
    }
}
