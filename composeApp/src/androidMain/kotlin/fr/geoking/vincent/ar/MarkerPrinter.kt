package fr.geoking.vincent.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream

/**
 * Prints the AR markers through the Android print framework, rendering each one at an exact physical
 * size (so ARCore's [MARKER_WIDTH_METERS] hint matches the printed target). No extra dependency: a
 * [PrintedPdfDocument] is drawn in PostScript points (1pt = 1/72").
 */
object MarkerPrinter {

    /** Physical side of each printed marker, in millimetres (matches the AR size hint). */
    private const val MARKER_SIZE_MM = 50f
    private const val POINTS_PER_MM = 72f / 25.4f
    private const val MARKER_SIZE_PT = MARKER_SIZE_MM * POINTS_PER_MM

    fun printMarkers(context: Context, jobName: String, items: List<Pair<String, Bitmap>>) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        printManager.print(jobName, MarkerAdapter(context, jobName, items), null)
    }

    private class MarkerAdapter(
        private val context: Context,
        private val jobName: String,
        private val items: List<Pair<String, Bitmap>>,
    ) : PrintDocumentAdapter() {

        private var pdf: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            pdf = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("$jobName.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            val document = pdf ?: run {
                callback.onWriteFailed("No document")
                return
            }
            val page = document.startPage(0)
            drawPage(page.canvas)
            document.finishPage(page)

            try {
                FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
                return
            } finally {
                document.close()
                pdf = null
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }

        private fun drawPage(canvas: Canvas) {
            val pageW = canvas.width.toFloat()
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 12f
                textAlign = Paint.Align.CENTER
            }
            val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
            val marginTop = 48f
            val gap = 40f
            val labelGap = 16f
            var y = marginTop
            items.forEach { (label, bitmap) ->
                val left = (pageW - MARKER_SIZE_PT) / 2f
                canvas.drawText(label, pageW / 2f, y, labelPaint)
                val top = y + labelGap
                val dst = RectF(left, top, left + MARKER_SIZE_PT, top + MARKER_SIZE_PT)
                canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), dst, imagePaint)
                y = top + MARKER_SIZE_PT + gap
            }
        }
    }
}
