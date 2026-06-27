package fr.geoking.vincent.ar

import android.opengl.Matrix
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Pose
import fr.geoking.vincent.model.NormPoint
import fr.geoking.vincent.model.RackArCalibration

/** Screen-space position (pixels) of a rack cell, plus whether it is in front of the camera. */
data class CellScreenPos(val cellIndex: Int, val x: Float, val y: Float, val visible: Boolean)

/**
 * Maps each rack cell to a screen position by placing the [cols]x[rows] grid over the
 * tracked [AugmentedImage] face (refined by the calibration quad) and projecting through
 * the ARCore camera matrices. Pure math, no rendering.
 */
object ArProjection {

    /** Bilinear point inside the calibration quad (TL, TR, BR, BL) for grid fraction (fu, fv). */
    private fun quadPoint(cal: RackArCalibration, fu: Float, fv: Float): NormPoint {
        val (tl, tr, br, bl) = cal.corners
        val topX = tl.x + (tr.x - tl.x) * fu
        val topY = tl.y + (tr.y - tl.y) * fu
        val botX = bl.x + (br.x - bl.x) * fu
        val botY = bl.y + (br.y - bl.y) * fu
        return NormPoint(topX + (botX - topX) * fv, topY + (botY - topY) * fv)
    }

    /**
     * @param viewMatrix column-major view matrix from `Camera.getViewMatrix`
     * @param projMatrix column-major projection matrix from `Camera.getProjectionMatrix`
     * @param widthPx / @param heightPx size of the AR view in pixels
     */
    fun cellPositions(
        image: AugmentedImage,
        calibration: RackArCalibration,
        cols: Int,
        rows: Int,
        staggered: Boolean,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        widthPx: Int,
        heightPx: Int,
    ): List<CellScreenPos> {
        if (!calibration.isValid || cols <= 0 || rows <= 0 || widthPx <= 0 || heightPx <= 0) return emptyList()

        val viewProj = FloatArray(16)
        Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)

        val center = image.centerPose
        val extentX = image.extentX
        val extentZ = image.extentZ
        val out = FloatArray(4)
        val point = FloatArray(4)

        val result = ArrayList<CellScreenPos>(cols * rows)
        for (row in 0 until rows) {
            val rowShift = if (staggered && row % 2 == 1) 0.5f else 0f
            for (col in 0 until cols) {
                val fu = (col + 0.5f + rowShift) / (if (staggered) cols + 0.5f else cols.toFloat())
                val fv = (row + 0.5f) / rows
                val p = quadPoint(calibration, fu, fv)
                // Augmented-image local frame: +X right, +Z down the image, +Y is the normal.
                val localX = (p.x - 0.5f) * extentX
                val localZ = (p.y - 0.5f) * extentZ
                val world: Pose = center.compose(Pose.makeTranslation(localX, 0f, localZ))

                point[0] = world.tx(); point[1] = world.ty(); point[2] = world.tz(); point[3] = 1f
                Matrix.multiplyMV(out, 0, viewProj, 0, point, 0)
                val w = out[3]
                if (w <= 0f) {
                    result += CellScreenPos(row * cols + col, 0f, 0f, false)
                    continue
                }
                val ndcX = out[0] / w
                val ndcY = out[1] / w
                val sx = (ndcX * 0.5f + 0.5f) * widthPx
                val sy = (1f - (ndcY * 0.5f + 0.5f)) * heightPx
                val onScreen = ndcX in -1.2f..1.2f && ndcY in -1.2f..1.2f
                result += CellScreenPos(row * cols + col, sx, sy, onScreen)
            }
        }
        return result
    }
}
