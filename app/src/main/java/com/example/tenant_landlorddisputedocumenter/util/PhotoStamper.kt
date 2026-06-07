package com.example.tenant_landlorddisputedocumenter.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Burns timestamp + GPS + capturer ID into the bottom strip of a photo so the proof is visible even
 * if the file is later separated from its metadata. Returns the path of the stamped file.
 */
object PhotoStamper {

    data class Stamp(
        val timestampMillis: Long,
        val latitude: Double?,
        val longitude: Double?,
        val capturedByUid: String,
    )

    fun stampInPlace(file: File, stamp: Stamp): File {
        // Downsample full-resolution camera shots to avoid OutOfMemory on a 12MP+ image
        // (decoding at native size and then allocating a second canvas bitmap can exceed
        // the per-app heap on low-end devices).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return file
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMEN)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return file
        // BitmapFactory does not apply EXIF orientation, so rotate the pixels upright before
        // drawing — otherwise the stamp lands on the wrong edge and the saved photo is sideways.
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val source = applyOrientation(decoded, orientation)
        val output = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)

        val stripHeight = (source.height * 0.10f).coerceAtLeast(120f)
        val stripPaint = Paint().apply {
            color = Color.argb(170, 0, 0, 0)
        }
        canvas.drawRect(0f, source.height - stripHeight, source.width.toFloat(), source.height.toFloat(), stripPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = stripHeight / 4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val tsLine = "@ ${DateUtils.formatReadable(stamp.timestampMillis)}"
        val gpsLine = if (stamp.latitude != null && stamp.longitude != null)
            "GPS ${"%.5f".format(stamp.latitude)}, ${"%.5f".format(stamp.longitude)}"
        else "GPS unavailable"
        val byLine = "By ${stamp.capturedByUid.take(8)}"

        val padding = stripHeight / 8f
        val lineHeight = textPaint.textSize + padding / 2
        val firstLineY = source.height - stripHeight + textPaint.textSize + padding
        canvas.drawText(tsLine, padding, firstLineY, textPaint)
        canvas.drawText(gpsLine, padding, firstLineY + lineHeight, textPaint)
        canvas.drawText(byLine, padding, firstLineY + 2 * lineHeight, textPaint)

        FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        source.recycle()
        output.recycle()

        // Also write EXIF for machine-parseable proof.
        writeExif(file, stamp)
        return file
    }

    private fun writeExif(file: File, stamp: Stamp) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(stamp.timestampMillis)),
            )
            // GPS — write as DMS-rational triplets since android.media.ExifInterface lacks setLatLong on older APIs.
            val lat = stamp.latitude
            val lon = stamp.longitude
            if (lat != null && lon != null) {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latLonToDms(lat))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, latLonToDms(lon))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
            }
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "ProofNest by=${stamp.capturedByUid}")
            // Pixels were already rotated upright before re-encoding; record NORMAL so viewers
            // don't rotate the image a second time.
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString(),
            )
            exif.saveAttributes()
        }
    }

    /** Largest dimension (px) kept when stamping; bounds memory use on high-res captures. */
    private const val MAX_DIMEN = 2048

    private fun calcInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxDim) {
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /** Convert a signed decimal lat/lon to the EXIF "deg/1,min/1,sec/100" rational format. */
    private fun latLonToDms(value: Double): String {
        val abs = kotlin.math.abs(value)
        val deg = abs.toInt()
        val minFloat = (abs - deg) * 60
        val min = minFloat.toInt()
        val secHundredths = ((minFloat - min) * 60 * 100).toInt()
        return "$deg/1,$min/1,$secHundredths/100"
    }

    /** Draw a placeholder thumbnail for previewing — used when we just need a static crop quickly. */
    fun cropThumbBytes(bytes: ByteArray, maxDim: Int = 600): ByteArray {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val scale = maxDim.toFloat() / maxOf(src.width, src.height)
        if (scale >= 1f) return bytes
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        src.recycle()
        scaled.recycle()
        return out.toByteArray()
    }

    /** No-op helper to avoid an unused-import warning when ExifInterface is the only consumer. */
    @Suppress("unused")
    private val keepRectAlive = Rect()
}
