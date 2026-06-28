package my.silentmode.pentana

import my.silentmode.pentana.core.excerpt
import my.silentmode.pentana.core.firstName
import my.silentmode.pentana.core.myr
import my.silentmode.pentana.core.relativeTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun money_prefixes_myr() = assertEquals("MYR 70.00", myr("70.00"))

    @Test fun firstName_takes_first_token() = assertEquals("Aisyah", firstName("Aisyah Rahman"))

    @Test fun firstName_handles_blank() = assertEquals("there", firstName(""))

    @Test fun excerpt_strips_html_and_truncates() =
        assertEquals("Hello world", excerpt("<p>Hello <b>world</b></p>"))

    @Test fun relativeTime_hours() =
        assertEquals("2h", relativeTime(epochMillisNow = 7_200_000L, createdMillis = 0L))
}
