package my.silentmode.pentana.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DataConversionTest {

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun copies_all_bytes_exactly() {
        val source = ByteArray(64 * 1024) { index -> (index % 251).toByte() }
        val nsData = source.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = source.size.toULong())
        }
        val copied = byteArrayFrom(nsData)
        assertContentEquals(source, copied)
    }

    @Test
    fun empty_data_gives_empty_array() {
        assertEquals(0, byteArrayFrom(NSData()).size)
    }
}
