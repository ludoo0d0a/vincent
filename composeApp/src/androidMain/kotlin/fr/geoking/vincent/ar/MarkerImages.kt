package fr.geoking.vincent.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

/**
 * Procedurally generates the Augmented Image marker used as the AR coordinate-frame beacon for
 * the MARKERS mode. The pattern is deterministic per [markerId] (so the same image
 * is fed to ARCore on every session) and is feature-rich + asymmetric to score well as an ARCore
 * augmented image target. The same bitmap is shown on screen for the user to print and stick on
 * the rack, so no binary asset needs to be bundled.
 */
object MarkerImages {

    fun bitmap(markerId: String, sizePx: Int = 640): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val rnd = Random(markerId.hashCode().toLong())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val margin = sizePx * 0.08f
        val inner = sizePx - 2f * margin
        val n = 8
        val cell = inner / n

        // Random grayscale blocks: strong local contrast across the whole target.
        for (gy in 0 until n) {
            for (gx in 0 until n) {
                val shade = rnd.nextInt(256)
                paint.color = Color.rgb(shade, shade, shade)
                val x = margin + gx * cell
                val y = margin + gy * cell
                canvas.drawRect(x, y, x + cell, y + cell, paint)
            }
        }

        // Overlay coloured circles for extra, multi-scale features.
        repeat(26) {
            paint.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            val cx = margin + rnd.nextFloat() * inner
            val cy = margin + rnd.nextFloat() * inner
            val r = cell * (0.18f + rnd.nextFloat() * 0.55f)
            canvas.drawCircle(cx, cy, r, paint)
        }

        // Asymmetric corner anchor so ARCore can resolve a unique orientation.
        paint.color = Color.BLACK
        canvas.drawRect(margin, margin, margin + cell * 1.2f, margin + cell * 0.7f, paint)

        // Quiet outer frame border to delimit the printable target.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.012f
        paint.color = Color.BLACK
        canvas.drawRect(
            margin * 0.5f,
            margin * 0.5f,
            sizePx - margin * 0.5f,
            sizePx - margin * 0.5f,
            paint,
        )
        return bmp
    }
}
