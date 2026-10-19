package my.silentmode.pentana

import my.silentmode.pentana.core.UPDATE_CHECK_INTERVAL_MILLIS
import my.silentmode.pentana.core.isUpdateCheckDue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckScheduleTest {
    private val lastChecked = 1_000_000_000L

    @Test fun due_when_never_checked() = assertTrue(isUpdateCheckDue(nowMillis = lastChecked, lastCheckedMillis = null))

    @Test fun not_due_within_a_day() =
        assertFalse(isUpdateCheckDue(nowMillis = lastChecked + UPDATE_CHECK_INTERVAL_MILLIS - 1, lastCheckedMillis = lastChecked))

    @Test fun due_after_a_day() =
        assertTrue(isUpdateCheckDue(nowMillis = lastChecked + UPDATE_CHECK_INTERVAL_MILLIS, lastCheckedMillis = lastChecked))

    @Test fun due_when_the_clock_went_backwards() =
        assertTrue(isUpdateCheckDue(nowMillis = lastChecked - 1, lastCheckedMillis = lastChecked))
}
