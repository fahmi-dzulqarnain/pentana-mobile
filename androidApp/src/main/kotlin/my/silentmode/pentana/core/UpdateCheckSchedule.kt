package my.silentmode.pentana.core

import android.content.Context

/** How often the app asks the server for a newer sideloaded build. */
const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

/** True when no check has succeeded yet, the last one is a day old, or the clock went backwards. */
fun isUpdateCheckDue(nowMillis: Long, lastCheckedMillis: Long?): Boolean =
    lastCheckedMillis == null ||
        nowMillis - lastCheckedMillis >= UPDATE_CHECK_INTERVAL_MILLIS ||
        nowMillis < lastCheckedMillis

/** Remembers when the update check last succeeded, so the prompt appears at most once a day. */
class UpdateCheckSchedule(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pentana_updates", Context.MODE_PRIVATE)

    fun isDue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isUpdateCheckDue(nowMillis, prefs.getLong(KEY, 0L).takeIf { it > 0L })

    fun markChecked(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY, nowMillis).apply()
    }

    private companion object {
        const val KEY = "last_checked_at"
    }
}
