package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.LunchDto

/** Shared decision - each platform maps this to its own chip styling. */
enum class LunchStatus { VoteNow, Responded, Closed }

fun lunchStatus(lunch: LunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.isOpen && lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

/**
 * The closed card's one-line result. On a day with several menus it also names the menu the pick
 * came from, since closed cards no longer list the options.
 */
fun lunchClosedSummary(lunch: LunchDto): String {
    if (!lunch.responded) return "Ordering closed: no order placed."
    val name = if (lunch.myLunchOptionId != null) {
        val menu = lunch.menus.firstOrNull { m -> m.options.any { it.lunchOptionId == lunch.myLunchOptionId } }
        val option = menu?.options?.firstOrNull { it.lunchOptionId == lunch.myLunchOptionId }?.name
        val menuName = if (lunch.menus.size > 1) menu?.menu ?: menu?.caterer else null
        if (option != null && menuName != null) "$option ($menuName)" else option
    } else if (lunch.myMealOptionId != null) {
        lunch.options.firstOrNull { it.mealOptionId == lunch.myMealOptionId }?.name
    } else {
        return "Ordering closed: you marked not attending."
    }
    return if (name != null) "Ordering closed: you ordered $name." else "Ordering closed: order placed."
}
