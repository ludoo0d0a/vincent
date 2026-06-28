package fr.geoking.vincent.ar

import com.google.ar.core.Pose
import kotlin.math.sqrt

/**
 * Bridges a BoofCV fiducial pose into an ARCore world [Pose] that matches the convention the rest of
 * the AR code expects (the same one ARCore Augmented Images used to provide).
 *
 * Two coordinate-frame conversions are applied:
 *  1. BoofCV reports the marker pose in the computer-vision camera frame (+X right, +Y down,
 *     +Z forward). ARCore's camera frame is OpenGL-style (+X right, +Y up, -Z forward). They differ
 *     by a 180-degrees rotation about X (negate Y and Z), after which we compose with the live
 *     camera world pose to get the marker in world space.
 *  2. BoofCV's marker-local frame is "+X right, +Y up, +Z out of the paper", whereas the grid math
 *     ([measureFromMarkers]/[ArProjection]) expects "+X right, +Z down the face, +Y the normal".
 *     Those differ by a +90-degrees rotation about X applied on the marker-local side.
 *
 * This is the highest-risk math in the feature; validate it by checking that the drawn marker
 * highlight lands exactly on the printed marker before trusting the projected grid.
 */
object ArPoseBridge {

    // +90 degrees about X: remaps BoofCV's marker frame to the rack-face frame. Matches the value
    // ArManualView uses to make a grid fronto-parallel.
    private val RACK_ALIGN = floatArrayOf(0.70710678f, 0f, 0f, 0.70710678f)

    /** World pose (rack-face convention) of the marker seen in [sighting], given the [cameraPose]. */
    fun markerWorldPose(sighting: FiducialSighting, cameraPose: Pose): Pose {
        val r = sighting.pose.R
        val t = sighting.pose.T

        // R_gl = diag(1,-1,-1) * R_cv  (negate rows 1 and 2).
        val m = DoubleArray(9) { r.get(it / 3, it % 3) }
        for (c in 0..2) {
            m[3 + c] = -m[3 + c]
            m[6 + c] = -m[6 + c]
        }
        val markerInCamera = Pose(
            floatArrayOf(t.x.toFloat(), (-t.y).toFloat(), (-t.z).toFloat()),
            rotationToQuaternion(m),
        )
        val world = cameraPose.compose(markerInCamera)
        return world.compose(Pose(floatArrayOf(0f, 0f, 0f), RACK_ALIGN))
    }

    /** Row-major 3x3 rotation matrix to a normalised ARCore quaternion (x, y, z, w). */
    private fun rotationToQuaternion(m: DoubleArray): FloatArray {
        val m00 = m[0]; val m01 = m[1]; val m02 = m[2]
        val m10 = m[3]; val m11 = m[4]; val m12 = m[5]
        val m20 = m[6]; val m21 = m[7]; val m22 = m[8]
        val trace = m00 + m11 + m22
        val qx: Double; val qy: Double; val qz: Double; val qw: Double
        if (trace > 0) {
            val s = sqrt(trace + 1.0) * 2.0
            qw = 0.25 * s
            qx = (m21 - m12) / s
            qy = (m02 - m20) / s
            qz = (m10 - m01) / s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1.0 + m00 - m11 - m22) * 2.0
            qw = (m21 - m12) / s
            qx = 0.25 * s
            qy = (m01 + m10) / s
            qz = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1.0 + m11 - m00 - m22) * 2.0
            qw = (m02 - m20) / s
            qx = (m01 + m10) / s
            qy = 0.25 * s
            qz = (m12 + m21) / s
        } else {
            val s = sqrt(1.0 + m22 - m00 - m11) * 2.0
            qw = (m10 - m01) / s
            qx = (m02 + m20) / s
            qy = (m12 + m21) / s
            qz = 0.25 * s
        }
        val n = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).takeIf { it > 0.0 } ?: 1.0
        return floatArrayOf((qx / n).toFloat(), (qy / n).toFloat(), (qz / n).toFloat(), (qw / n).toFloat())
    }
}
