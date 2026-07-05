package fr.geoking.vincent.data

import fr.geoking.vincent.getCurrentTimeMillis
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.ArMode
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.model.FlavorProfile
import fr.geoking.vincent.model.NormPoint
import fr.geoking.vincent.model.Producer
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackArAnchor
import fr.geoking.vincent.model.RackArCalibration
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackFormat
import fr.geoking.vincent.model.Region
import fr.geoking.vincent.model.SugarLevel
import fr.geoking.vincent.model.Supplier
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val VINCENT_BACKUP_FORMAT = "vincent-backup"
const val VINCENT_BACKUP_VERSION = 1
const val VINCENT_MANIFEST_FILE = "manifest.json"

private val backupJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Serializable
data class VincentManifestDto(
    val format: String = VINCENT_BACKUP_FORMAT,
    val version: Int = VINCENT_BACKUP_VERSION,
    val exportedAt: Long = 0,
    val includesPhotos: Boolean = false,
    val bottles: List<BottleDto> = emptyList(),
    val racks: List<RackDto> = emptyList(),
    val tastings: List<TastingDto> = emptyList(),
    val producers: List<ProducerDto> = emptyList(),
    val suppliers: List<SupplierDto> = emptyList(),
    val regions: List<RegionDto> = emptyList(),
)

@Serializable data class FlavorProfileDto(
    val sweetness: Int,
    val acidity: Int,
    val tannins: Int,
    val alcohol: Int,
    val body: Int,
    val finish: Int,
)

@Serializable data class BottleDto(
    val id: String,
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
    val alcoholLevel: Double = 0.0,
    val sugarLevel: String = SugarLevel.SEC.name,
    val favorite: Boolean = false,
    val pairings: List<String> = emptyList(),
    val drinkFrom: Int = 0,
    val drinkTo: Int = 0,
    val drinkNow: Float = 0.5f,
    val agingPotential: Int = 0,
    val tastingNotes: String = "",
    val description: String = "",
    val pairingNotes: String = "",
    val grapes: List<String> = emptyList(),
    val flavorProfile: FlavorProfileDto? = null,
    val maturity: String = "",
    val source: String = AddSource.MANUAL.name,
    val addedLabel: String = "",
    val photoBottle: String? = null,
    val photoLabel: String? = null,
    val photoBack: String? = null,
    val addedAt: Long = 0,
)

@Serializable data class NormPointDto(val x: Float, val y: Float)

@Serializable data class RackArCalibrationDto(val corners: List<NormPointDto>)

@Serializable data class RackArAnchorDto(
    val markerId: String,
    val markerWidthMeters: Float,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
    val gridWidthMeters: Float,
    val gridHeightMeters: Float,
    val tlFiducialId: Int = -1,
    val brFiducialId: Int = -1,
)

@Serializable data class RackCellDto(
    val row: String,
    val occupied: Boolean,
    val color: String? = null,
    val category: String? = null,
    val vintage: String? = null,
    val price: Int? = null,
    val selected: Boolean = false,
)

@Serializable data class RackDto(
    val id: String,
    val name: String,
    val cols: Int,
    val rows: Int,
    val staggered: Boolean,
    val cells: List<RackCellDto>,
    val format: String = RackFormat.GRID.name,
    val staggerOffset: Boolean = false,
    val arImagePath: String? = null,
    val arCalibration: RackArCalibrationDto? = null,
    val arMode: String = ArMode.PHOTO.name,
    val arAnchor: RackArAnchorDto? = null,
)

@Serializable data class TastingDto(
    val id: String,
    val bottleId: String? = null,
    val wineName: String,
    val date: String,
    val rating: Double,
    val notes: String,
    val color: String? = null,
    val vintage: String? = null,
    val place: String = "",
)

@Serializable data class ProducerDto(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "",
    val website: String = "",
    val email: String = "",
    val phone: String = "",
)

@Serializable data class SupplierDto(
    val id: String,
    val name: String,
    val type: String = "",
    val website: String = "",
    val email: String = "",
    val phone: String = "",
)

@Serializable data class RegionDto(
    val id: String,
    val name: String,
    val country: String = "",
    val description: String = "",
)

fun encodeVincentManifest(manifest: VincentManifestDto): String =
    backupJson.encodeToString(VincentManifestDto.serializer(), manifest)

fun decodeVincentManifest(json: String): VincentManifestDto =
    backupJson.decodeFromString(VincentManifestDto.serializer(), json)

fun buildManifestDto(includePhotos: Boolean): VincentManifestDto = VincentManifestDto(
    exportedAt = getCurrentTimeMillis(),
    includesPhotos = includePhotos,
    bottles = Cellar.bottles.map { it.toDto() },
    racks = Racks.all.map { it.toDto() },
    tastings = Tastings.all.map { it.toDto() },
    producers = Producers.all.map { it.toDto() },
    suppliers = Suppliers.all.map { it.toDto() },
    regions = Regions.all.map { it.toDto() },
)

private fun Bottle.toDto() = BottleDto(
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
    alcoholLevel = alcoholLevel,
    sugarLevel = sugarLevel.name,
    favorite = favorite,
    pairings = pairings,
    drinkFrom = drinkFrom,
    drinkTo = drinkTo,
    drinkNow = drinkNow,
    agingPotential = agingPotential,
    tastingNotes = tastingNotes,
    description = description,
    pairingNotes = pairingNotes,
    grapes = grapes,
    flavorProfile = flavorProfile?.toDto(),
    maturity = maturity,
    source = source.name,
    addedLabel = addedLabel,
    photoBottle = photoBottle,
    photoLabel = photoLabel,
    photoBack = photoBack,
    addedAt = addedAt,
)

private fun FlavorProfile.toDto() = FlavorProfileDto(
    sweetness, acidity, tannins, alcohol, body, finish,
)

private fun Rack.toDto() = RackDto(
    id = id,
    name = name,
    cols = cols,
    rows = rows,
    staggered = staggered,
    cells = cells.map { it.toDto() },
    format = format.name,
    staggerOffset = staggerOffset,
    arImagePath = arImagePath,
    arCalibration = arCalibration?.toDto(),
    arMode = arMode.name,
    arAnchor = arAnchor?.toDto(),
)

private fun RackCell.toDto() = RackCellDto(
    row = row,
    occupied = occupied,
    color = color?.name,
    category = category?.name,
    vintage = vintage,
    price = price,
    selected = selected,
)

private fun RackArCalibration.toDto() = RackArCalibrationDto(
    corners = corners.map { NormPointDto(it.x, it.y) },
)

private fun RackArAnchor.toDto() = RackArAnchorDto(
    markerId, markerWidthMeters, tx, ty, tz, qx, qy, qz, qw,
    gridWidthMeters, gridHeightMeters, tlFiducialId, brFiducialId,
)

private fun Tasting.toDto() = TastingDto(
    id, bottleId, wineName, date, rating, notes, color?.name, vintage, place,
)

private fun Producer.toDto() = ProducerDto(id, name, region, country, website, email, phone)

private fun Supplier.toDto() = SupplierDto(id, name, type, website, email, phone)

private fun Region.toDto() = RegionDto(id, name, country, description)

fun VincentManifestDto.toDomain(): VincentBackupData = VincentBackupData(
    bottles = bottles.map { it.toDomain() },
    racks = racks.map { it.toDomain() },
    tastings = tastings.map { it.toDomain() },
    producers = producers.map { it.toDomain() },
    suppliers = suppliers.map { it.toDomain() },
    regions = regions.map { it.toDomain() },
    includesPhotos = includesPhotos,
)

data class VincentBackupData(
    val bottles: List<Bottle>,
    val racks: List<Rack>,
    val tastings: List<Tasting>,
    val producers: List<Producer>,
    val suppliers: List<Supplier>,
    val regions: List<Region>,
    val includesPhotos: Boolean,
)

private fun BottleDto.toDomain() = Bottle(
    id = id,
    domain = domain,
    appellation = appellation,
    color = enumOr(WineColor.RED, color),
    category = enumOr(WineCategory.BORDEAUX, category),
    vintage = vintage,
    price = price,
    quantity = quantity,
    rating = rating,
    cellarSpot = cellarSpot,
    provenance = provenance,
    merchant = merchant,
    purchaseDate = purchaseDate,
    occasion = occasion,
    alcoholLevel = alcoholLevel,
    sugarLevel = enumOr(SugarLevel.SEC, sugarLevel),
    favorite = favorite,
    pairings = pairings,
    drinkFrom = drinkFrom,
    drinkTo = drinkTo,
    drinkNow = drinkNow,
    agingPotential = agingPotential,
    tastingNotes = tastingNotes,
    description = description,
    pairingNotes = pairingNotes,
    grapes = grapes,
    flavorProfile = flavorProfile?.toDomain(),
    maturity = maturity,
    source = enumOr(AddSource.MANUAL, source),
    addedLabel = addedLabel,
    photoBottle = photoBottle,
    photoLabel = photoLabel,
    photoBack = photoBack,
    addedAt = addedAt,
)

private fun FlavorProfileDto.toDomain() = FlavorProfile(
    sweetness, acidity, tannins, alcohol, body, finish,
)

private fun RackDto.toDomain() = Rack(
    name = name,
    cols = cols,
    rows = rows,
    staggered = staggered,
    cells = cells.map { it.toDomain() },
    format = enumOr(RackFormat.GRID, format),
    staggerOffset = staggerOffset,
    id = id,
    arImagePath = arImagePath,
    arCalibration = arCalibration?.toDomain(),
    arMode = enumOr(ArMode.PHOTO, arMode),
    arAnchor = arAnchor?.toDomain(),
)

private fun RackCellDto.toDomain() = RackCell(
    row = row,
    occupied = occupied,
    color = color?.let { enumOr(WineColor.RED, it) },
    category = category?.let { enumOr(WineCategory.BORDEAUX, it) },
    vintage = vintage,
    price = price,
    selected = selected,
)

private fun RackArCalibrationDto.toDomain() = RackArCalibration(
    corners = corners.map { NormPoint(it.x, it.y) },
)

private fun RackArAnchorDto.toDomain() = RackArAnchor(
    markerId, markerWidthMeters, tx, ty, tz, qx, qy, qz, qw,
    gridWidthMeters, gridHeightMeters, tlFiducialId, brFiducialId,
)

private fun TastingDto.toDomain() = Tasting(
    id, bottleId, wineName, date, rating, notes,
    color?.let { enumOr(WineColor.RED, it) }, vintage, place,
)

private fun ProducerDto.toDomain() = Producer(id, name, region, country, website, email, phone)

private fun SupplierDto.toDomain() = Supplier(id, name, type, website, email, phone)

private fun RegionDto.toDomain() = Region(id, name, country, description)

private inline fun <reified T : Enum<T>> enumOr(default: T, raw: String): T =
    runCatching { enumValueOf<T>(raw) }.getOrDefault(default)

fun safeArchiveId(id: String): String =
    id.replace(Regex("[^a-zA-Z0-9_-]"), "-").ifBlank { "item" }

fun bottlePhotoArchivePath(bottleId: String, kind: BottlePhotoKind): String =
    "photos/bottles/${safeArchiveId(bottleId)}-${kind.suffix}.jpg"

fun rackPhotoArchivePath(rackId: String): String =
    "photos/racks/${safeArchiveId(rackId)}.jpg"

fun isRemotePhotoPath(path: String?): Boolean =
    !path.isNullOrBlank() && (path.startsWith("http://") || path.startsWith("https://"))

fun isArchivePhotoPath(path: String?): Boolean =
    !path.isNullOrBlank() && path.startsWith("photos/")
