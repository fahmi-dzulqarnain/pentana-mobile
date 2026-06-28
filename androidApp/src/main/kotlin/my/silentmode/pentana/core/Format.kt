package my.silentmode.pentana.core

import java.time.Instant
import java.time.OffsetDateTime

/** Money fields arrive as fixed-2dp strings from the API; just prefix the currency. */
fun myr(amount: String): String = "MYR $amount"

/** First token of a full name, for the "Hi, {first}" greeting. */
fun firstName(fullName: String): String =
    fullName.trim().substringBefore(' ').ifBlank { "there" }

/** Strip rich-text HTML to a plain, single-line, ellipsized excerpt. */
fun excerpt(text: String, max: Int = 140): String {
    val plain = text
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (plain.length <= max) plain else plain.take(max).trimEnd() + "…"
}

/** Compact relative time from a millis delta: "now" / "{m}m" / "{h}h" / "{d}d". */
fun relativeTime(epochMillisNow: Long, createdMillis: Long): String {
    val secs = (epochMillisNow - createdMillis).coerceAtLeast(0L) / 1000
    return when {
        secs < 60 -> "now"
        secs < 3600 -> "${secs / 60}m"
        secs < 86_400 -> "${secs / 3600}h"
        else -> "${secs / 86_400}d"
    }
}

/** Parse an ISO-8601 timestamp (Laravel `created_at`, etc.) to epoch millis; null if unparseable. */
fun parseIso(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

/** Relative time for a UI timestamp string; "" when missing/unparseable. */
fun relativeTimeFrom(createdAt: String?): String {
    val created = parseIso(createdAt) ?: return ""
    return relativeTime(System.currentTimeMillis(), created)
}

/** Medium date-time for activity cards, e.g. "Thu 3 Jul · 7:00 AM". Falls back to the raw string. */
fun dateTimeMedium(iso: String?): String {
    val millis = parseIso(iso) ?: return iso ?: ""
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", java.util.Locale.ENGLISH))
}

/** Long date for the Home greeting hero, e.g. "Friday, 27 June". */
fun todayLong(): String =
    java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.ENGLISH))

/** Up to two uppercase initials for an avatar. */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
