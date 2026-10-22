package my.silentmode.pentana.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A picked receipt image, already converted to an upload-ready JPEG, plus a small [preview] and
 * name/size for the confirm tile.
 */
class PickedPhoto(val bytes: ByteArray, val name: String, val sizeLabel: String, val preview: Bitmap)

/** Longest side of the uploaded image; plenty to read a receipt. */
internal const val PROOF_MAX_DIMENSION = 2000

/** Longest side of the confirm-tile thumbnail: sharp at its on-screen size without holding the full image. */
internal const val PREVIEW_MAX_DIMENSION = 360

/** Stays under the server's 5 MB limit with room for the multipart overhead. */
internal const val PROOF_MAX_BYTES = 4_500_000

private val PROOF_QUALITIES = listOf(80, 65, 50)

/**
 * Read the photo at a content [uri] from the Photo Picker and re-encode it as a JPEG, or null when it
 * can't be decoded. The server only accepts jpg/png/webp, and many phones save HEIF by default, so
 * the original bytes can't be sent as-is. Re-encoding also bakes in the EXIF rotation and drops the
 * metadata (GPS location included). Slow on large photos: call it off the main thread.
 */
fun readPhoto(context: Context, uri: Uri): PickedPhoto? {
    val resolver = context.contentResolver
    val bitmap = runCatching { decodeScaled(context, uri) }.getOrNull() ?: return null
    val bytes = compressWithin(PROOF_MAX_BYTES, PROOF_QUALITIES) { quality ->
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }
    val (w, h) = fitWithin(bitmap.width, bitmap.height, PREVIEW_MAX_DIMENSION)
    val preview = Bitmap.createScaledBitmap(bitmap, w, h, true)
    // createScaledBitmap hands back the same bitmap when the photo is already that small.
    if (preview !== bitmap) bitmap.recycle()
    var name = "receipt.jpg"
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.let { name = jpegName(it) }
    }
    return PickedPhoto(bytes, name, formatBytes(bytes.size), preview)
}

private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder reads HEIF and applies the EXIF orientation itself.
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val (w, h) = fitWithin(info.size.width, info.size.height, PROOF_MAX_DIMENSION)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // hardware bitmaps can't be compressed
        }
    }

    // Android 8.x has no HEIF decoder, but camera photos there are JPEG; rotate per EXIF by hand.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, PROOF_MAX_DIMENSION)
    }
    val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val degrees = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
    val (w, h) = fitWithin(sampled.width, sampled.height, PROOF_MAX_DIMENSION)
    val matrix = Matrix().apply {
        postScale(w / sampled.width.toFloat(), h / sampled.height.toFloat())
        postRotate(degrees.toFloat())
    }
    return Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
        .also { if (it !== sampled) sampled.recycle() }
}

private fun exifRotation(exif: ExifInterface): Int =
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

/** Scale [width] x [height] down so the longest side is at most [maxDimension]; never scales up. */
internal fun fitWithin(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxDimension) return width to height
    val scale = maxDimension.toDouble() / longest
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/** The largest power-of-two subsample that still leaves the longest side at least [maxDimension]. */
internal fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxDimension) sample *= 2
    return sample
}

/** Encode at each quality in turn until the result fits in [maxBytes]; the last attempt is kept regardless. */
internal fun compressWithin(maxBytes: Int, qualities: List<Int>, encode: (quality: Int) -> ByteArray): ByteArray {
    var bytes = ByteArray(0)
    for (quality in qualities) {
        bytes = encode(quality)
        if (bytes.size <= maxBytes) break
    }
    return bytes
}

/** "IMG_0042.HEIC" -> "IMG_0042.jpg", since the upload is now a JPEG. */
internal fun jpegName(displayName: String): String {
    val base = displayName.substringBeforeLast('.', displayName).ifBlank { "receipt" }
    return "$base.jpg"
}

private fun formatBytes(byteCount: Int): String = when {
    byteCount >= 1_000_000 -> "%.1f MB".format(byteCount / 1_000_000.0)
    byteCount >= 1_000 -> "${byteCount / 1000} KB"
    else -> "$byteCount B"
}
