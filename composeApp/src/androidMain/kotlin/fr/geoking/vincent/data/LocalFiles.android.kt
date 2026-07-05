package fr.geoking.vincent.data

import java.io.File

actual suspend fun readLocalBytes(path: String): ByteArray? {
    if (path.isBlank()) return null
    if (isRemotePhotoPath(path)) return null
    val file = when {
        path.startsWith("file://") -> File(path.removePrefix("file://"))
        else -> File(path)
    }
    return if (file.isFile) file.readBytes() else null
}
