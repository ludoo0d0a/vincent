package fr.geoking.vincent.ai

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private object MlKitLabelOcr : LabelOcr {
    private val client by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        if (jpeg.isEmpty()) return@withContext ""
        try {
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                ?: return@withContext ""
            val result = client.process(InputImage.fromBitmap(bitmap, 0)).await()
            result.text.trim()
        } catch (_: Exception) {
            ""
        }
    }
}

actual fun labelOcr(): LabelOcr = MlKitLabelOcr
