package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto

/** Shared decision - each platform maps this to its own chip/pill styling. */
enum class DashboardActivityStatus { Registered, Waitlisted, None }

/**
 * Status chip for the next-lunch card. Both platforms agree `responded` wins over closed
 * (unlike the Lunch screen's [lunchStatus], where closed wins).
 */
fun dashboardLunchStatus(lunch: DashboardLunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

fun dashboardActivityStatus(activity: DashboardActivityDto): DashboardActivityStatus = when (activity.myStatus) {
    "registered" -> DashboardActivityStatus.Registered
    "waitlisted" -> DashboardActivityStatus.Waitlisted
    else -> DashboardActivityStatus.None
}

/** One "Needs you" row on Home. */
sealed interface HomeTodo {
    data class RejectedProof(val proofId: Long, val amount: String, val reason: String?) : HomeTodo
    data class Dues(val amountDue: String, val unpaidCount: Int, val credit: String, val inReview: String?) : HomeTodo
    data class LunchVote(val lunch: DashboardLunchDto) : HomeTodo
    data class SupportReply(val count: Int, val ticketId: Long?, val subject: String?) : HomeTodo
}

/** Everything Home renders; both platforms draw from this so they never disagree on to-dos or order. */
data class HomeSections(
    val todos: List<HomeTodo>,
    /** Pending proofs total when they cover all outstanding dues (the "Proof in review" row), else null. */
    val proofInReview: String?,
    val comingUpLunch: DashboardLunchDto?,
    val comingUpActivity: DashboardActivityDto?,
    val openActivitiesCount: Int,
)

/** Urgency order: rejected proof, dues, lunch vote, support reply. Uncapped; the UI shows the first three. */
fun homeSections(dashboard: DashboardDto): HomeSections {
    val outstanding = moneyCents(dashboard.bills.totalOutstanding)
    val pending = moneyCents(dashboard.bills.pendingProofsAmount)
    val todos = mutableListOf<HomeTodo>()
    var proofInReview: String? = null

    dashboard.rejectedProof?.let {
        todos += HomeTodo.RejectedProof(it.id, it.amountClaimed, it.adminNote)
    }
    if (outstanding > 0) {
        if (pending >= outstanding) {
            proofInReview = formatCents(pending)
        } else {
            todos += HomeTodo.Dues(
                amountDue = formatCents(outstanding - pending),
                unpaidCount = dashboard.bills.unpaidCount,
                credit = dashboard.bills.availableCredit,
                inReview = if (pending > 0) formatCents(pending) else null,
            )
        }
    }
    val next = dashboard.nextLunch
    val voteTodo = next != null && next.isOpen && !next.responded
    if (voteTodo) todos += HomeTodo.LunchVote(next!!)
    if (dashboard.supportRepliesWaiting > 0) {
        val t = dashboard.supportReplyTicket
        todos += HomeTodo.SupportReply(dashboard.supportRepliesWaiting, t?.id, t?.subject)
    }
    return HomeSections(
        todos = todos,
        proofInReview = proofInReview,
        comingUpLunch = if (voteTodo) dashboard.otherLunch else next,
        comingUpActivity = dashboard.nextActivity,
        openActivitiesCount = dashboard.openActivitiesCount,
    )
}

/** "120" / "120.5" / "120.00" -> cents, without floating point. Malformed input counts as 0. */
internal fun moneyCents(amount: String): Long {
    val s = amount.trim()
    val negative = s.startsWith("-")
    val body = s.removePrefix("-")
    val whole = body.substringBefore('.')
    val frac = body.substringAfter('.', "").padEnd(2, '0').take(2)
    val w = (if (whole.isEmpty()) "0" else whole).toLongOrNull() ?: return 0
    val f = frac.toLongOrNull() ?: return 0
    if (w < 0 || f < 0) return 0
    val cents = w * 100 + f
    return if (negative) -cents else cents
}

/** 3550 -> "35.50". */
internal fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val a = kotlin.math.abs(cents)
    return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}
