package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.LunchDto

/** Shared decision - each platform maps this to its own chip styling. */
enum class LunchStatus { VoteNow, Responded, Closed }

fun lunchStatus(lunch: LunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.isOpen && lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

fun lunchClosedSummary(lunch: LunchDto): String {
    if (!lunch.responded) return "Ordering closed: no order placed."
    val name = if (lunch.myLunchOptionId != null) {
        lunch.menus.flatMap { it.options }.firstOrNull { it.lunchOptionId == lunch.myLunchOptionId }?.name
    } else if (lunch.myMealOptionId != null) {
        lunch.options.firstOrNull { it.mealOptionId == lunch.myMealOptionId }?.name
    } else {
        return "Ordering closed: you marked not attending."
    }
    return if (name != null) "Ordering closed: you ordered $name." else "Ordering closed: order placed."
}
