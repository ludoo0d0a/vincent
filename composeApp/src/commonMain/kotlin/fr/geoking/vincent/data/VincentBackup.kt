package fr.geoking.vincent.data

import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.photo
import fr.geoking.vincent.model.withPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class VincentImportMode { MERGE, REPLACE }

data class VincentImportResult(
    val bottles: Int,
    val racks: Int,
    val tastings: Int,
    val producers: Int,
    val suppliers: Int,
    val regions: Int,
    val photosRestored: Int,
)

data class VincentParsedBackup(
    val manifest: VincentManifestDto,
    val photos: Map<String, ByteArray>,
)

object VincentBackup {

    suspend fun buildExport(
        includePhotos: Boolean,
        readLocal: suspend (String) -> ByteArray?,
    ): ByteArray = withContext(Dispatchers.Default) {
        val manifest = buildManifestDto(includePhotos = includePhotos)
        val photos = mutableMapOf<String, ByteArray>()
        val bottles = manifest.bottles.map { dto ->
            if (!includePhotos) {
                dto.copy(photoBottle = null, photoLabel = null, photoBack = null)
            } else {
                dto.copy(
                    photoBottle = embedPhoto(dto.photoBottle, dto.id, BottlePhotoKind.BOTTLE, photos, readLocal),
                    photoLabel = embedPhoto(dto.photoLabel, dto.id, BottlePhotoKind.LABEL, photos, readLocal),
                    photoBack = embedPhoto(dto.photoBack, dto.id, BottlePhotoKind.BACK, photos, readLocal),
                )
            }
        }
        val racks = manifest.racks.map { dto ->
            if (!includePhotos) {
                dto.copy(arImagePath = dto.arImagePath?.takeIf { isRemotePhotoPath(it) })
            } else {
                dto.copy(
                    arImagePath = embedRackPhoto(dto.arImagePath, dto.id, photos, readLocal),
                )
            }
        }
        val finalManifest = manifest.copy(bottles = bottles, racks = racks)
        val json = encodeVincentManifest(finalManifest)
        if (includePhotos) {
            VincentArchive.pack(json, photos)
        } else {
            json.encodeToByteArray()
        }
    }

    fun parseImport(bytes: ByteArray): VincentParsedBackup {
        val payload = VincentArchive.unpack(bytes)
        val manifest = decodeVincentManifest(payload.manifestJson)
        if (manifest.format != VINCENT_BACKUP_FORMAT) {
            error("unsupported format")
        }
        return VincentParsedBackup(manifest, payload.photos)
    }

    suspend fun applyImport(
        parsed: VincentParsedBackup,
        mode: VincentImportMode,
        labelSaver: LabelImageSaver,
        rackSaver: RackImageSaver,
    ): VincentImportResult = withContext(Dispatchers.Default) {
        if (mode == VincentImportMode.REPLACE) {
            clearAll()
        }
        val data = parsed.manifest.toDomain()
        var photosRestored = 0

        val bottles = data.bottles.map { bottle ->
            val (restored, count) = restoreBottlePhotos(bottle, parsed.photos, labelSaver)
            photosRestored += count
            restored
        }
        val racks = data.racks.map { rack ->
            val (restored, count) = restoreRackPhoto(rack, parsed.photos, rackSaver)
            photosRestored += count
            restored
        }

        Cellar.importBottles(bottles)
        Racks.import(racks)
        Tastings.import(data.tastings)
        Producers.import(data.producers)
        Suppliers.import(data.suppliers)
        Regions.import(data.regions)

        VincentImportResult(
            bottles = bottles.size,
            racks = racks.size,
            tastings = data.tastings.size,
            producers = data.producers.size,
            suppliers = data.suppliers.size,
            regions = data.regions.size,
            photosRestored = photosRestored,
        )
    }

    suspend fun clearAll() {
        cloudSyncClearAll()
        Cellar.clearAll()
        Racks.clearAll()
        Tastings.clearAll()
        Producers.clearAll()
        Suppliers.clearAll()
        Regions.clearAll()
    }

    private suspend fun embedPhoto(
        path: String?,
        bottleId: String,
        kind: BottlePhotoKind,
        photos: MutableMap<String, ByteArray>,
        readLocal: suspend (String) -> ByteArray?,
    ): String? {
        if (path.isNullOrBlank()) return null
        if (isRemotePhotoPath(path)) return path
        val bytes = readLocal(path) ?: return null
        val archivePath = bottlePhotoArchivePath(bottleId, kind)
        photos[archivePath] = bytes
        return archivePath
    }

    private suspend fun embedRackPhoto(
        path: String?,
        rackId: String,
        photos: MutableMap<String, ByteArray>,
        readLocal: suspend (String) -> ByteArray?,
    ): String? {
        if (path.isNullOrBlank()) return null
        if (isRemotePhotoPath(path)) return path
        val bytes = readLocal(path) ?: return null
        val archivePath = rackPhotoArchivePath(rackId)
        photos[archivePath] = bytes
        return archivePath
    }

    private suspend fun restoreBottlePhotos(
        bottle: Bottle,
        photos: Map<String, ByteArray>,
        labelSaver: LabelImageSaver,
    ): Pair<Bottle, Int> {
        var restored = bottle
        var count = 0
        for (kind in BottlePhotoKind.entries) {
            val path = bottle.photo(kind) ?: continue
            if (isRemotePhotoPath(path)) continue
            if (!isArchivePhotoPath(path)) continue
            val bytes = photos[path] ?: continue
            val localPath = labelSaver.save(bytes, bottle.id, kind)
            restored = restored.withPhoto(kind, localPath)
            count++
        }
        return restored to count
    }

    private suspend fun restoreRackPhoto(
        rack: Rack,
        photos: Map<String, ByteArray>,
        rackSaver: RackImageSaver,
    ): Pair<Rack, Int> {
        val path = rack.arImagePath ?: return rack to 0
        if (isRemotePhotoPath(path)) return rack to 0
        if (!isArchivePhotoPath(path)) return rack to 0
        val bytes = photos[path] ?: return rack to 0
        val localPath = rackSaver.save(bytes, rack.id)
        return rack.copy(arImagePath = localPath) to 1
    }
}
