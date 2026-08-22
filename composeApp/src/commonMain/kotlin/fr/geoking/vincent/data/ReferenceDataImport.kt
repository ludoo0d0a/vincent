package fr.geoking.vincent.data

import fr.geoking.vincent.model.Appellation
import fr.geoking.vincent.model.Grape
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val refJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class GrapeImportDto(
    val id: String = "",
    val name: String,
    val color: String = "",
    val vivcNumber: Int = 0,
    val country: String = "",
    val aliases: List<String> = emptyList(),
)

@Serializable
data class AppellationImportDto(
    val id: String = "",
    val name: String,
    val sign: String = "",
    val category: String = "",
    val department: String = "",
    val inaoId: Int = 0,
    val geoAsset: String = "",
)

@Serializable
private data class GrapeImportFile(val grapes: List<GrapeImportDto> = emptyList())

@Serializable
private data class AppellationImportFile(val appellations: List<AppellationImportDto> = emptyList())

object ReferenceDataImport {

    fun parseGrapesJson(text: String): List<Grape> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        return when {
            trimmed.startsWith("[") -> refJson.decodeFromString<List<GrapeImportDto>>(trimmed).map { it.toDomain() }
            else -> refJson.decodeFromString<GrapeImportFile>(trimmed).grapes.map { it.toDomain() }
        }
    }

    fun parseAppellationsJson(text: String): List<Appellation> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        return when {
            trimmed.startsWith("[") -> refJson.decodeFromString<List<AppellationImportDto>>(trimmed).map { it.toDomain() }
            else -> refJson.decodeFromString<AppellationImportFile>(trimmed).appellations.map { it.toDomain() }
        }
    }

    private fun GrapeImportDto.toDomain(): Grape {
        val stableId = when {
            id.isNotBlank() -> id
            vivcNumber > 0 -> "vivc-$vivcNumber"
            else -> "grape-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        }
        return Grape(
            id = stableId,
            name = name.trim(),
            color = normalizeGrapeColor(color),
            vivcNumber = vivcNumber,
            country = country.trim(),
            aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() && !it.equals(name, ignoreCase = true) },
        )
    }

    private fun AppellationImportDto.toDomain(): Appellation {
        val stableId = when {
            id.isNotBlank() -> id
            inaoId > 0 -> "inao-$inaoId"
            else -> "app-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        }
        return Appellation(
            id = stableId,
            name = name.trim(),
            sign = sign.trim(),
            category = category.trim(),
            department = department.trim(),
            inaoId = inaoId,
            geoAsset = geoAsset.trim(),
        )
    }

    private fun normalizeGrapeColor(raw: String): String = when (raw.lowercase().trim()) {
        "red", "rouge", "r", "noir" -> "red"
        "white", "blanc", "w", "b" -> "white"
        "rose", "rosé", "pink" -> "rose"
        else -> raw.lowercase().trim()
    }
}
