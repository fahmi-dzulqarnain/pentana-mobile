package my.silentmode.pentana.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** A picked receipt image: raw bytes + display name/size for the confirm tile. */
class PickedPhoto(val bytes: ByteArray, val name: String, val sizeLabel: String)

/** Read the bytes + display name of a content [uri] returned by the Android Photo Picker. */
fun readPhoto(context: Context, uri: Uri): PickedPhoto {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    var name = "receipt.jpg"
    resolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.let { name = it }
    }
    return PickedPhoto(bytes, name, formatBytes(bytes.size))
}

private fun formatBytes(n: Int): String = when {
    n >= 1_000_000 -> "%.1f MB".format(n / 1_000_000.0)
    n >= 1_000 -> "${n / 1000} KB"
    else -> "$n B"
}
