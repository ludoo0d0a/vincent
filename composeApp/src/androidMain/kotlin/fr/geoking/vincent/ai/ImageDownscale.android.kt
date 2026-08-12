package fr.geoking.vincent.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a label JPEG before Gemini vision upload (and for OCR when helpful).
 * No-op when already small enough or decode fails.
 */
internal fun downscaleJpeg(
    jpeg: ByteArray,
    maxEdge: Int = 1280,
    quality: Int = 70,
): ByteArray {
    if (jpeg.isEmpty()) return jpeg
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return jpeg
    val longest = max(w, h)
    if (longest <= maxEdge && jpeg.size <= 350_000) return jpeg

    var sample = 1
    while (longest / sample > maxEdge * 2) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        jpeg,
        0,
        jpeg.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return jpeg

    val dw = decoded.width
    val dh = decoded.height
    val scale = maxEdge.toFloat() / max(dw, dh).toFloat()
    val scaled = if (scale < 1f) {
        val tw = (dw * scale).roundToInt().coerceAtLeast(1)
        val th = (dh * scale).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, tw, th, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } else {
        decoded
    }

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 95), out)
    scaled.recycle()
    val bytes = out.toByteArray()
    return if (bytes.isNotEmpty() && bytes.size < jpeg.size) bytes else jpeg
}
