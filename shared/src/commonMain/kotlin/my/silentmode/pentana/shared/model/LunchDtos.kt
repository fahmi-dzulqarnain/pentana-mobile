package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LunchOptionDto(
    @SerialName("meal_option_id") val mealOptionId: Long,
    val name: String? = null,
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
    val responded: Boolean = false,
    @SerialName("my_meal_option_id") val myMealOptionId: Long? = null,
)

/** POST /api/v1/lunches/{id}/respond body — null mealOptionId = "not attending". */
@Serializable
data class RespondRequest(
    @SerialName("meal_option_id") val mealOptionId: Long?,
)
