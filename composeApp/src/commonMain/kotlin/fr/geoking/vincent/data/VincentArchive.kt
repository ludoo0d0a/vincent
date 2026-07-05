package fr.geoking.vincent.data

data class VincentArchivePayload(
    val manifestJson: String,
    val photos: Map<String, ByteArray> = emptyMap(),
)

expect object VincentArchive {
    fun pack(manifestJson: String, photos: Map<String, ByteArray>): ByteArray
    fun unpack(bytes: ByteArray): VincentArchivePayload
}
