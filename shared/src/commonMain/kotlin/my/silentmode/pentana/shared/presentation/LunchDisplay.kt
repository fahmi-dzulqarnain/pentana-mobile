package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.LunchDto

/** Shared decision — each platform maps this to its own chip styling. */
enum class LunchStatus { VoteNow, Responded, Closed }

fun lunchStatus(lunch: LunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.isOpen && lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

fun lunchClosedSummary(lunch: LunchDto): String {
    if (!lunch.responded) return "Ordering closed — no order placed."
    if (lunch.myMealOptionId == null) return "Ordering closed — you marked not attending."
    val name = lunch.options.firstOrNull { it.mealOptionId == lunch.myMealOptionId }?.name
    return if (name != null) "Ordering closed — you ordered $name." else "Ordering closed — order placed."
}
