package fr.geoking.vincent.ar

import android.opengl.Matrix
import com.google.ar.core.Pose
import fr.geoking.vincent.model.NormPoint
import fr.geoking.vincent.model.RackArAnchor
import fr.geoking.vincent.model.RackArCalibration

/** Screen-space position (pixels) of a rack cell, plus whether it is in front of the camera. */
data class CellScreenPos(val cellIndex: Int, val x: Float, val y: Float, val visible: Boolean)

/**
 * Maps each rack cell to a position. Two paths share the same grid layout:
 * - [cellQuadPoint] for the 2D PHOTO mode (bilinear point inside the calibration quad).
 * - [cellPositions] for MARKERS mode (3D grid frame projected through the camera).
 * Pure math, no rendering.
 */
object ArProjection {

    /**
     * Projects a single world-space [point] (x, y, z, in metres) through the camera, returning its
     * pixel position and whether it is in front of and roughly within the viewport. Shares the
     * NDC->screen logic with [cellPositions]; used to draw the marked zone overlay during setup.
     */
    fun projectWorldPoint(
        point: FloatArray,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        widthPx: Int,
        heightPx: Int,
    ): CellScreenPos {
        if (widthPx <= 0 || heightPx <= 0) return CellScreenPos(-1, 0f, 0f, false)
        val viewProj = FloatArray(16)
        Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)
        val out = FloatArray(4)
        val p = floatArrayOf(point[0], point[1], point[2], 1f)
        Matrix.multiplyMV(out, 0, viewProj, 0, p, 0)
        val w = out[3]
        if (w <= 0f) return CellScreenPos(-1, 0f, 0f, false)
        val ndcX = out[0] / w
        val ndcY = out[1] / w
        val sx = (ndcX * 0.5f + 0.5f) * widthPx
        val sy = (1f - (ndcY * 0.5f + 0.5f)) * heightPx
        val onScreen = ndcX in -1.2f..1.2f && ndcY in -1.2f..1.2f
        return CellScreenPos(-1, sx, sy, onScreen)
    }

    /**
     * Four world-space corners (metres) of a detected Augmented Image, derived from its
     * [centerPose] and physical [extentX] (local X) x [extentZ] (local Z). Order is TL, TR, BR, BL
     * in the marker's own plane. Used to outline each detected marker on screen.
     */
    fun markerCorners(centerPose: Pose, extentX: Float, extentZ: Float): List<FloatArray> {
        val hx = extentX / 2f
        val hz = extentZ / 2f
        return listOf(
            floatArrayOf(-hx, 0f, -hz),
            floatArrayOf(hx, 0f, -hz),
            floatArrayOf(hx, 0f, hz),
            floatArrayOf(-hx, 0f, hz),
        ).map { local ->
            val p = centerPose.compose(Pose.makeTranslation(local[0], local[1], local[2]))
            floatArrayOf(p.tx(), p.ty(), p.tz())
        }
    }

    /**
     * Grid fraction (fu, fv) in 0..1 for the centre of the cell at [col],[row], honouring the
     * half-cell [staggered] (quinconce) shift on odd rows.
     */
    fun gridFraction(col: Int, row: Int, cols: Int, rows: Int, staggered: Boolean): Pair<Float, Float> {
        val rowShift = if (staggered && row % 2 == 1) 0.5f else 0f
        val fu = (col + 0.5f + rowShift) / (if (staggered) cols + 0.5f else cols.toFloat())
        val fv = (row + 0.5f) / rows
        return fu to fv
    }

    /** World pose of the grid origin: the tracked marker [markerCenterPose] composed with the stored relative transform. */
    fun gridFrame(markerCenterPose: Pose, anchor: RackArAnchor): Pose {
        val relative = Pose(
            floatArrayOf(anchor.tx, anchor.ty, anchor.tz),
            floatArrayOf(anchor.qx, anchor.qy, anchor.qz, anchor.qw),
        )
        return markerCenterPose.compose(relative)
    }

    /** Bilinear point inside the calibration quad (TL, TR, BR, BL) for grid fraction (fu, fv). */
    private fun quadPoint(cal: RackArCalibration, fu: Float, fv: Float): NormPoint {
        val (tl, tr, br, bl) = cal.corners
        val topX = tl.x + (tr.x - tl.x) * fu
        val topY = tl.y + (tr.y - tl.y) * fu
        val botX = bl.x + (br.x - bl.x) * fu
        val botY = bl.y + (br.y - bl.y) * fu
        return NormPoint(topX + (botX - topX) * fv, topY + (botY - topY) * fv)
    }

    /** Normalised (0..1) point of a cell centre inside the calibration quad, for the 2D PHOTO overlay. */
    fun cellQuadPoint(
        calibration: RackArCalibration,
        col: Int,
        row: Int,
        cols: Int,
        rows: Int,
        staggered: Boolean,
    ): NormPoint {
        val (fu, fv) = gridFraction(col, row, cols, rows, staggered)
        return quadPoint(calibration, fu, fv)
    }

    /**
     * Projects every rack cell of the [cols]x[rows] grid through the ARCore camera, given a
     * [gridFrame] pose whose local X/Z plane carries the grid (centre at the frame origin),
     * with physical [gridWidthMeters] x [gridHeightMeters] extents.
     *
     * @param viewMatrix column-major view matrix from `Camera.getViewMatrix`
     * @param projMatrix column-major projection matrix from `Camera.getProjectionMatrix`
     * @param widthPx / @param heightPx size of the AR view in pixels
     */
    fun cellPositions(
        gridFrame: Pose,
        gridWidthMeters: Float,
        gridHeightMeters: Float,
        cols: Int,
        rows: Int,
        staggered: Boolean,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        widthPx: Int,
        heightPx: Int,
    ): List<CellScreenPos> {
        if (cols <= 0 || rows <= 0 || widthPx <= 0 || heightPx <= 0) return emptyList()
        if (gridWidthMeters <= 0f || gridHeightMeters <= 0f) return emptyList()

        val viewProj = FloatArray(16)
        Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)

        val out = FloatArray(4)
        val point = FloatArray(4)

        val result = ArrayList<CellScreenPos>(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val (fu, fv) = gridFraction(col, row, cols, rows, staggered)
                // Grid local frame: +X right across the face, +Z down the face, +Y is the normal.
                val localX = (fu - 0.5f) * gridWidthMeters
                val localZ = (fv - 0.5f) * gridHeightMeters
                val world: Pose = gridFrame.compose(Pose.makeTranslation(localX, 0f, localZ))

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
