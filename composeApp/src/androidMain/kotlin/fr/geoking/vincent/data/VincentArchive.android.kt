package fr.geoking.vincent.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object VincentArchive {

    actual fun pack(manifestJson: String, photos: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(VINCENT_MANIFEST_FILE))
            zip.write(manifestJson.encodeToByteArray())
            zip.closeEntry()
            photos.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    actual fun unpack(bytes: ByteArray): VincentArchivePayload {
        if (!isZip(bytes)) {
            return VincentArchivePayload(bytes.decodeToString())
        }
        var manifestJson: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes()
                when {
                    entry.name == VINCENT_MANIFEST_FILE || entry.name.endsWith("/$VINCENT_MANIFEST_FILE") ->
                        manifestJson = content.decodeToString()
                    entry.name.startsWith("photos/") && !entry.isDirectory ->
                        photos[entry.name.removePrefix("./")] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return VincentArchivePayload(
            manifestJson = manifestJson ?: error("missing manifest"),
            photos = photos,
        )
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()
}
