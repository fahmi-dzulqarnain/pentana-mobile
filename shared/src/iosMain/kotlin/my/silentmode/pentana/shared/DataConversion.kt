package my.silentmode.pentana.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Bulk-copies [data] into a Kotlin ByteArray with a single memcpy. Swift callers should prefer
 * this over building a KotlinByteArray element-by-element — per-index `set` calls cross the
 * ObjC bridge once per byte, which is millions of calls for a camera photo.
 */
@OptIn(ExperimentalForeignApi::class)
fun byteArrayFrom(data: NSData): ByteArray {
    val length = data.length
    require(length <= Int.MAX_VALUE.toULong()) { "NSData too large for a ByteArray: $length bytes" }
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, length)
    }
    return bytes
}
