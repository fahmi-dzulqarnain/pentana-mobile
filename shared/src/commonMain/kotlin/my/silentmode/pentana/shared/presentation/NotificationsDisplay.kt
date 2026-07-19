package my.silentmode.pentana.shared.presentation

/** Shared decision — each platform maps this to its own icon + colours. */
enum class NotificationKind { Lunch, Cancelled, Payment, ActivityJoined, Activity, General }

/**
 * NotificationDto has no type field — infer the kind from the title text.
 * Keyword lists are the union of what Android and iOS matched before sharing; check order matters
 * (e.g. "Activity cancelled" must read as Cancelled, while "Lunch cancelled" stays Lunch — lunch is
 * checked first, matching both platforms' original order).
 */
fun notificationKind(title: String): NotificationKind = when {
    title.contains("lunch", ignoreCase = true) -> NotificationKind.Lunch
    title.contains("cancel", ignoreCase = true) -> NotificationKind.Cancelled
    title.contains("proof", ignoreCase = true) || title.contains("payment", ignoreCase = true) || title.contains("dues", ignoreCase = true) -> NotificationKind.Payment
    title.contains("you're in", ignoreCase = true) || title.contains("promoted", ignoreCase = true) || title.contains("waitlist", ignoreCase = true) -> NotificationKind.ActivityJoined
    title.contains("activity", ignoreCase = true) || title.contains("spot", ignoreCase = true) || title.contains("event", ignoreCase = true) || title.contains("hik", ignoreCase = true) || title.contains("clean", ignoreCase = true) || title.contains("workshop", ignoreCase = true) -> NotificationKind.Activity
    else -> NotificationKind.General
}
