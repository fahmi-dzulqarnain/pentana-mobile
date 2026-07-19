package my.silentmode.pentana.shared.presentation

/** Shared decision — each platform maps this to its own icon + colours. */
enum class NotificationKind { Lunch, Cancelled, Payment, ActivityJoined, Activity, General }

/**
 * NotificationDto has no type field — infer the kind from the title text.
 * Keyword lists are the union of what Android and iOS matched before sharing; check order matters
 * (e.g. "Activity cancelled" must read as Cancelled).
 */
fun notificationKind(title: String): NotificationKind {
    val t = title.lowercase()
    return when {
        "lunch" in t -> NotificationKind.Lunch
        "cancel" in t -> NotificationKind.Cancelled
        "proof" in t || "payment" in t || "dues" in t -> NotificationKind.Payment
        "you're in" in t || "promoted" in t || "waitlist" in t -> NotificationKind.ActivityJoined
        "activity" in t || "spot" in t || "event" in t || "hik" in t || "clean" in t || "workshop" in t -> NotificationKind.Activity
        else -> NotificationKind.General
    }
}
