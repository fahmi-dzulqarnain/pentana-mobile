package my.silentmode.pentana.core

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads public images (activity description pictures) by URL - no auth header, no extra library.
 * Decoded at most [MAX_DIMENSION_PX] on the long side and kept in a small in-memory cache so
 * reopening an activity doesn't refetch.
 */
object RemoteImages {
    private const val MAX_DIMENSION_PX = 1600
    private const val MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024
    private const val TIMEOUT_MS = 15_000

    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(url: String): ImageBitmap? = cache.get(url)

    /** Null when the download fails or the bytes aren't an image. */
    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val bytes = withContext(Dispatchers.IO) { download(url) } ?: return null
        val bitmap = withContext(Dispatchers.Default) { decode(bytes) } ?: return null
        cache.put(url, bitmap)
        return bitmap
    }

    private fun download(url: String): ByteArray? {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return null
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { stream ->
                val bytes = stream.readNBytesCompat(MAX_DOWNLOAD_BYTES + 1)
                if (bytes.size > MAX_DOWNLOAD_BYTES) null else bytes
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (output.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
