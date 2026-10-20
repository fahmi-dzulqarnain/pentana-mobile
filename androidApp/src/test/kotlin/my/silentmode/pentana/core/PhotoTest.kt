package my.silentmode.pentana.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoTest {
    @Test
    fun fit_within_scales_the_longest_side_down_and_keeps_the_aspect() {
        assertEquals(2000 to 1500, fitWithin(4032, 3024, 2000))
        assertEquals(1500 to 2000, fitWithin(3024, 4032, 2000))
    }

    @Test
    fun fit_within_never_scales_a_small_photo_up() {
        assertEquals(1170 to 2532, fitWithin(1170, 2532, 3000))
        assertEquals(800 to 600, fitWithin(800, 600, 2000))
    }

    @Test
    fun sample_size_stays_at_or_above_the_target() {
        assertEquals(2, sampleSize(4032, 3024, 2000)) // 2016 px
        assertEquals(4, sampleSize(8160, 6120, 2000)) // 2040 px
        assertEquals(1, sampleSize(1920, 1080, 2000))
    }

    @Test
    fun compress_within_stops_at_the_first_quality_that_fits() {
        val tried = mutableListOf<Int>()
        val bytes = compressWithin(100, listOf(80, 65, 50)) { quality ->
            tried += quality
            ByteArray(if (quality == 65) 90 else 200)
        }
        assertEquals(listOf(80, 65), tried)
        assertEquals(90, bytes.size)
    }

    @Test
    fun compress_within_keeps_the_last_attempt_when_none_fit() {
        val bytes = compressWithin(10, listOf(80, 50)) { quality -> ByteArray(quality) }
        assertEquals(50, bytes.size)
    }

    @Test
    fun jpeg_name_replaces_the_extension() {
        assertEquals("IMG_0042.jpg", jpegName("IMG_0042.HEIC"))
        assertEquals("receipt.jpg", jpegName("receipt.png"))
        assertEquals("scan.jpg", jpegName("scan"))
        assertEquals("receipt.jpg", jpegName(".heic"))
    }
}
