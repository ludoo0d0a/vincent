package fr.geoking.vincent.ar

import android.media.Image
import boofcv.abst.fiducial.FiducialDetector
import boofcv.alg.distort.pinhole.LensDistortionPinhole
import boofcv.factory.fiducial.ConfigFiducialBinary
import boofcv.factory.fiducial.FactoryFiducial
import boofcv.factory.filter.binary.ConfigThreshold
import boofcv.factory.filter.binary.ThresholdType
import boofcv.struct.calib.CameraPinhole
import boofcv.struct.image.GrayU8
import georegression.struct.se.Se3_F64

/** A detected fiducial: its decoded numeric [id] and 3D [pose] (marker -> camera, BoofCV CV frame). */
class FiducialSighting(val id: Int, val pose: Se3_F64)

/**
 * Wraps a BoofCV square-binary [FiducialDetector] for use on ARCore camera frames. The detector is
 * created once; the lens model is (re)applied from the ARCore intrinsics whenever the processed
 * image size changes, which enables the 3D pose ([FiducialDetector.getFiducialToCamera]).
 *
 * Not thread-safe: [detect] mutates shared detector state, so a single in-flight detection must be
 * enforced by the caller.
 */
class BoofCvFiducials(targetWidthMeters: Double) {

    private val detector: FiducialDetector<GrayU8> = FactoryFiducial.squareBinary(
        ConfigFiducialBinary(targetWidthMeters),
        LOCAL_THRESHOLD,
        GrayU8::class.java,
    )

    private var lensWidth = 0
    private var lensHeight = 0

    /**
     * Detects every square-binary fiducial in [gray]. [fx]/[fy] are focal lengths and [cx]/[cy] the
     * principal point, all in pixels of [gray] (from `frame.camera.getImageIntrinsics()`).
     */
    fun detect(gray: GrayU8, fx: Double, fy: Double, cx: Double, cy: Double): List<FiducialSighting> {
        if (gray.width != lensWidth || gray.height != lensHeight) {
            val pinhole = CameraPinhole(fx, fy, 0.0, cx, cy, gray.width, gray.height)
            detector.setLensDistortion(LensDistortionPinhole(pinhole), gray.width, gray.height)
            lensWidth = gray.width
            lensHeight = gray.height
        }
        detector.detect(gray)
        val found = detector.totalFound()
        val out = ArrayList<FiducialSighting>(found)
        for (i in 0 until found) {
            val se3 = Se3_F64()
            if (detector.getFiducialToCamera(i, se3)) {
                out += FiducialSighting(detector.getId(i).toInt(), se3)
            }
        }
        return out
    }

    private companion object {
        // Adaptive local-mean threshold: robust to uneven lighting across the rack face.
        val LOCAL_THRESHOLD: ConfigThreshold = ConfigThreshold.local(ThresholdType.LOCAL_MEAN, 21)
    }
}

/**
 * Copies the luminance (Y) plane of an ARCore YUV_420_888 [image] into [dst] (reshaped as needed),
 * giving a grayscale image BoofCV can process. The Y plane is already the grayscale channel, so no
 * colour conversion is required.
 */
fun imageYToGray(image: Image, dst: GrayU8): GrayU8 {
    val w = image.width
    val h = image.height
    if (dst.width != w || dst.height != h) dst.reshape(w, h)
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val data = dst.data
    if (pixelStride == 1) {
        for (y in 0 until h) {
            buffer.position(y * rowStride)
            buffer.get(data, y * w, w)
        }
    } else {
        for (y in 0 until h) {
            var pos = y * rowStride
            val rowStart = y * w
            for (x in 0 until w) {
                data[rowStart + x] = buffer.get(pos)
                pos += pixelStride
            }
        }
    }
    return dst
}
