package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.LunchDto

/** Shared decision - each platform maps this to its own chip styling. */
enum class LunchStatus { VoteNow, Responded, Closed }

fun lunchStatus(lunch: LunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.isOpen && lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

/** Name of the option the member picked, or null when they haven't picked one (no response, or not attending). */
fun lunchPickedOptionName(lunch: LunchDto): String? = when {
    !lunch.responded -> null
    lunch.myLunchOptionId != null ->
        lunch.menus.flatMap { it.options }.firstOrNull { it.lunchOptionId == lunch.myLunchOptionId }?.name
    lunch.myMealOptionId != null -> lunch.options.firstOrNull { it.mealOptionId == lunch.myMealOptionId }?.name
    else -> null
}

/**
 * The closed card's one-line result. On a day with several menus it also names the menu the pick
 * came from, since the closed card's title shows only the option.
 */
fun lunchClosedSummary(lunch: LunchDto): String {
    if (!lunch.responded) return "Ordering closed: no order placed."
    if (lunch.myLunchOptionId == null && lunch.myMealOptionId == null) return "Ordering closed: you marked not attending."
    val option = lunchPickedOptionName(lunch) ?: return "Ordering closed: order placed."
    val menu = lunch.menus.firstOrNull { m -> m.options.any { it.lunchOptionId == lunch.myLunchOptionId } }
    val menuName = if (lunch.menus.size > 1) menu?.menu ?: menu?.caterer else null
    return if (menuName != null) "Ordering closed: you ordered $option ($menuName)." else "Ordering closed: you ordered $option."
}
