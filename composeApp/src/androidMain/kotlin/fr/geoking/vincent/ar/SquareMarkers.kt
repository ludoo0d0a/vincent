package fr.geoking.vincent.ar

import android.graphics.Bitmap
import boofcv.alg.drawing.FiducialImageEngine
import boofcv.alg.fiducial.square.FiducialSquareGenerator
import boofcv.android.ConvertBitmap
import boofcv.struct.image.GrayU8
import kotlin.math.abs

/**
 * Generates BoofCV square-binary fiducial markers (the AR coordinate-frame beacons for the MARKERS
 * mode) and derives the two stable numeric ids used for a rack's top-left (origin) and bottom-right
 * markers.
 *
 * Both the rendered bitmap and the live detector ([BoofCvFiducials]) must agree on [GRID_WIDTH] and
 * the default 0.25 black-border fraction so a printed marker decodes to the id it was generated for.
 * The marker is fully described by its numeric id, so nothing needs to be persisted as an image.
 */
object SquareMarkers {

    /** Inner grid is [GRID_WIDTH] x [GRID_WIDTH]; 4 encodes a 12-bit value (0..4095). */
    const val GRID_WIDTH: Int = 4

    /** Top-left (origin) marker id for the rack. Even, so it never collides with its [brId]. */
    fun tlId(rackId: String): Int = pairBase(rackId) * 2

    /** Bottom-right marker id for the rack. */
    fun brId(rackId: String): Int = pairBase(rackId) * 2 + 1

    // Keep ids well inside the 0..4095 range of a 4x4 binary grid (max base*2+1 = 3999).
    private fun pairBase(rackId: String): Int = abs(rackId.hashCode()) % 2000

    /**
     * Renders the square-binary fiducial encoding [value] into an ARGB bitmap. [markerPx] is the
     * black marker region; a white quiet zone of a quarter of that is added around it (required for
     * reliable detection).
     */
    fun bitmap(value: Int, markerPx: Int = 400): Bitmap {
        val borderPx = markerPx / 4
        val engine = ScaledImageEngine(markerPx, borderPx)
        val generator = FiducialSquareGenerator(engine)
        // generate() computes whiteBorderDoc/markerWidth; a non-zero markerWidth keeps it 0 (the
        // quiet zone is supplied by the engine's border) instead of producing NaN.
        generator.markerWidth = 1.0
        generator.generate(value.toLong(), GRID_WIDTH)
        return ConvertBitmap.grayToBitmap(engine.gray, Bitmap.Config.ARGB_8888)
    }
}

/**
 * [FiducialImageEngine] consumes coordinates in pixels, but [FiducialSquareGenerator] emits them in
 * a normalised 0..1 marker space (only the AWT engine, unavailable on Android, scales them). This
 * subclass multiplies the incoming coordinates by the marker pixel size before delegating, so the
 * exact BoofCV encoding is reproduced at full resolution.
 */
private class ScaledImageEngine(markerPx: Int, borderPx: Int) : FiducialImageEngine() {
    private val s = markerPx.toDouble()

    init {
        configure(borderPx, markerPx)
    }

    override fun rectangle(x0: Double, y0: Double, x1: Double, y1: Double) {
        super.rectangle(x0 * s, y0 * s, x1 * s, y1 * s)
    }

    override fun square(x0: Double, y0: Double, width0: Double, thickness: Double) {
        super.square(x0 * s, y0 * s, width0 * s, thickness * s)
    }

    override fun circle(cx: Double, cy: Double, radius: Double) {
        super.circle(cx * s, cy * s, radius * s)
    }

    override fun draw(image: GrayU8, x0: Double, y0: Double, x1: Double, y1: Double) {
        super.draw(image, x0 * s, y0 * s, x1 * s, y1 * s)
    }
}
