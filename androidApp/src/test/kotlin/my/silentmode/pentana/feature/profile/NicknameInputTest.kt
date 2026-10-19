package my.silentmode.pentana.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class NicknameInputTest {
    @Test
    fun shortValueIsUntouched() {
        assertEquals("Fahmi", truncateToCodePoints("Fahmi", 30))
    }

    @Test
    fun overLongPasteIsTruncatedNotDropped() {
        assertEquals("a".repeat(30), truncateToCodePoints("a".repeat(45), 30))
    }

    @Test
    fun countsCodePointsNotUtf16Units() {
        val emoji = "😀" // one code point, two UTF-16 units
        assertEquals(emoji.repeat(30), truncateToCodePoints(emoji.repeat(30), 30))
        assertEquals(emoji.repeat(30), truncateToCodePoints(emoji.repeat(40), 30))
    }
}
