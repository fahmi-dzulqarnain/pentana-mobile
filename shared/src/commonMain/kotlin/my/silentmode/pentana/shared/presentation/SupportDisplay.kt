package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.SupportTicketDetailDto

/** Shared decision - each platform maps this to its own pill/chip colours. */
enum class SupportStatus { OPEN, REPLIED, CLOSED }

/** The server's raw status. Anything this app version doesn't know reads as open. */
fun supportStatus(status: String): SupportStatus = when (status) {
    "waiting_on_member" -> SupportStatus.REPLIED
    "closed" -> SupportStatus.CLOSED
    else -> SupportStatus.OPEN
}

/** Short wording for list rows. */
fun supportListLabel(status: SupportStatus): String = when (status) {
    SupportStatus.OPEN -> "Open"
    SupportStatus.REPLIED -> "Replied"
    SupportStatus.CLOSED -> "Closed"
}

/** Fuller wording for the conversation header. */
fun supportHeaderLabel(status: SupportStatus): String = when (status) {
    SupportStatus.OPEN -> "Open"
    SupportStatus.REPLIED -> "Committee replied"
    SupportStatus.CLOSED -> "Closed"
}

fun supportTypeLabel(type: String): String = when (type) {
    "bug" -> "Bug"
    "suggestion" -> "Suggestion"
    else -> "Question"
}

/** The Home card's subtitle, with the number of committee replies the member hasn't answered. */
fun supportCardSubtitle(waiting: Int): String = when {
    waiting <= 0 -> SUPPORT_CARD_SUBTITLE
    waiting == 1 -> "$SUPPORT_CARD_SUBTITLE · 1 reply"
    else -> "$SUPPORT_CARD_SUBTITLE · $waiting replies"
}

/**
 * Who closed a ticket, or null while it's open. Dates are formatted per platform (the shared code
 * has no date library), so each app appends " · {date}" from [SupportTicketDetailDto.closedAt].
 */
fun closedNote(detail: SupportTicketDetailDto): String? = when {
    supportStatus(detail.status) != SupportStatus.CLOSED -> null
    detail.closedByCommittee -> "Closed by the committee"
    else -> "You marked this resolved"
}

private const val SUPPORT_CARD_SUBTITLE = "Questions, bugs or ideas"
