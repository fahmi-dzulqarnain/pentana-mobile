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
