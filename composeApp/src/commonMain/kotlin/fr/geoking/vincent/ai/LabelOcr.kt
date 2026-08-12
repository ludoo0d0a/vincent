package fr.geoking.vincent.ai

/** On-device OCR over a label JPEG. Returns raw recognised text (may be blank). */
interface LabelOcr {
    suspend fun recognize(jpeg: ByteArray): String
}

expect fun labelOcr(): LabelOcr
