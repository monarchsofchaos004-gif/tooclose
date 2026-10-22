package app.tooclose

import android.graphics.Bitmap
import android.media.FaceDetector
import androidx.camera.core.ImageProxy

/**
 * One camera frame → distance estimate.
 * Uses Android's built-in android.media.FaceDetector (no Google ML Kit, no network): it reports the distance
 * between the eyes in pixels. Pinhole camera: distance = focal_px × real eye distance ÷ eye distance in pixels.
 */
class Estimator {
    class Reading(val rawCm: Float?, val dark: Boolean)

    private var fd: FaceDetector? = null
    private var fdW = 0
    private var fdH = 0
    private val faces = arrayOfNulls<FaceDetector.Face>(1)

    /**
     * @param focalRatio focal length ÷ long side of the sensor (both mm), from Camera2 characteristics
     * @param ipdCm      assumed real eye distance
     */
    fun read(img: ImageProxy, focalRatio: Float, ipdCm: Float): Reading {
        val plane = img.planes[0]   // Y (luminance) is enough for face detection
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixStride = plane.pixelStride
        val srcW = img.width
        val srcH = img.height
        val step = maxOf(1, srcW / 320)
        val sw = srcW / step
        val sh = srcH / step
        val rot = img.imageInfo.rotationDegrees
        // Upright size (face detection needs upright faces); FaceDetector needs an even width
        var outW = if (rot == 90 || rot == 270) sh else sw
        val outH = if (rot == 90 || rot == 270) sw else sh
        outW = outW and 1.inv()
        val px = IntArray(outW * outH)
        var sum = 0L
        for (y in 0 until sh) {
            val row = y * step * rowStride
            for (x in 0 until sw) {
                val v = buf.get(row + x * step * pixStride).toInt() and 0xff
                sum += v
                val ox: Int
                val oy: Int
                when (rot) {
                    90 -> { ox = sh - 1 - y; oy = x }
                    180 -> { ox = sw - 1 - x; oy = sh - 1 - y }
                    270 -> { ox = y; oy = sw - 1 - x }
                    else -> { ox = x; oy = y }
                }
                if (ox < outW) px[oy * outW + ox] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val mean = sum / (sw.toLong() * sh).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, outW, outH, Bitmap.Config.RGB_565)
        if (fd == null || fdW != outW || fdH != outH) { fd = FaceDetector(outW, outH, 1); fdW = outW; fdH = outH }
        faces[0] = null
        val n = try { fd!!.findFaces(bmp, faces) } catch (e: Exception) { 0 }
        bmp.recycle()
        val f = faces[0]
        if (n < 1 || f == null || f.confidence() < FaceDetector.Face.CONFIDENCE_THRESHOLD) return Reading(null, mean < 12)
        val eyesPx = f.eyesDistance()
        if (eyesPx < 4f) return Reading(null, false)
        val focalPx = focalRatio * maxOf(outW, outH)
        return Reading(focalPx * ipdCm / eyesPx, false)
    }
}
