package io.github.lopezjavier.appfitness.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a camera or picker image to at most [MAX_SIDE] px on the long side and about [MAX_BYTES] as JPEG. The maths
 * lives in pure functions; [compress] is the thin Android wrapper (decode with `inSampleSize`, EXIF rotation, scale).
 */
object PhotoCompressor {
    const val MAX_SIDE = 1600
    const val MAX_BYTES = 1_500_000
    const val START_QUALITY = 90
    const val MIN_QUALITY = 40
    private const val QUALITY_STEP = 10
    private const val DEG_90 = 90
    private const val DEG_180 = 180
    private const val DEG_270 = 270

    /** Width and height after scaling so the long side is at most [MAX_SIDE]; smaller images are untouched. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        val longSide = max(width, height)
        if (longSide <= MAX_SIDE) return width to height
        val factor = MAX_SIDE.toFloat() / longSide
        return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
    }

    /** Largest power-of-two `inSampleSize` whose decoded long side is still at least [MAX_SIDE]. */
    fun sampleSize(width: Int, height: Int): Int {
        val longSide = max(width, height)
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    /** Clockwise degrees to apply for an EXIF orientation tag (flips are ignored). */
    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> DEG_90
        ExifInterface.ORIENTATION_ROTATE_180 -> DEG_180
        ExifInterface.ORIENTATION_ROTATE_270 -> DEG_270
        else -> 0
    }

    /** Encodes with decreasing quality until the output is at most [MAX_BYTES] (or [MIN_QUALITY] is reached). */
    fun encodeWithinLimit(encode: (quality: Int) -> ByteArray): ByteArray {
        var quality = START_QUALITY
        var bytes = encode(quality)
        while (bytes.size > MAX_BYTES && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP
            bytes = encode(quality)
        }
        return bytes
    }

    /** Reads [source], writes the compressed JPEG to [target]. Returns false if the image cannot be decoded. */
    @Suppress("ReturnCount")
    fun compress(source: File, target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeFile(source.path, options)
        if (decoded == null) return false
        val orientation = ExifInterface(source.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val oriented = transform(decoded, rotationDegrees(orientation))
        val bytes = encodeWithinLimit { quality ->
            ByteArrayOutputStream().also { oriented.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        }
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        target.writeBytes(bytes)
        return true
    }

    private fun transform(bitmap: Bitmap, degrees: Int): Bitmap {
        val (width, height) = targetSize(bitmap.width, bitmap.height)
        if (degrees == 0 && width == bitmap.width && height == bitmap.height) return bitmap
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
            val factor = width.toFloat() / bitmap.width
            postScale(factor, factor)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
