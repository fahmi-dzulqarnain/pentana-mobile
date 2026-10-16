package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LunchOptionDto(
    @SerialName("meal_option_id") val mealOptionId: Long,
    val name: String? = null,
)

/** One pickable meal on one menu. [lunchOptionId] is what the API takes; [mealOptionId] is the shared meal. */
@Serializable
data class LunchMenuOptionDto(
    @SerialName("lunch_option_id") val lunchOptionId: Long,
    @SerialName("meal_option_id") val mealOptionId: Long,
    val name: String? = null,
)

/** One menu (caterer + dish) of a lunch day; a day can hold several. */
@Serializable
data class LunchMenuDto(
    val id: Long,
    val caterer: String? = null,
    val menu: String? = null,
    val options: List<LunchMenuOptionDto> = emptyList(),
)

@Serializable
data class LunchDto(
    val id: Long,
    val date: String,
    val caterer: String? = null,
    val menu: String? = null,
    val deadline: String? = null,
    @SerialName("is_open") val isOpen: Boolean,
    val options: List<LunchOptionDto> = emptyList(),
    val menus: List<LunchMenuDto> = emptyList(),
    val responded: Boolean = false,
    @SerialName("my_meal_option_id") val myMealOptionId: Long? = null,
    @SerialName("my_lunch_option_id") val myLunchOptionId: Long? = null,
)

/**
 * POST /api/v1/lunches/{id}/respond body - null lunchOptionId = "not attending". Sent as an explicit
 * `"lunch_option_id":null` (no default, so it is always encoded).
 */
@Serializable
data class RespondRequest(
    @SerialName("lunch_option_id") val lunchOptionId: Long?,
)
