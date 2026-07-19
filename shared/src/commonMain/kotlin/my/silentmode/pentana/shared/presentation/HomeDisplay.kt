package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto

/** Shared decision — each platform maps this to its own chip/pill styling. */
enum class DashboardActivityStatus { Registered, Waitlisted, None }

/** "All clear" celebration card instead of the dues card. */
fun duesCleared(d: DashboardDto): Boolean =
    d.bills.totalOutstanding == "0.00" && d.pendingProofsCount == 0

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
