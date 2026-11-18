package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A per-activity registration question, from the activity's `registration_fields`.
 * `options` carries the choices for `select` questions; it is null/empty for the others.
 */
@Serializable
data class QuestionDto(
    val key: String,
    val label: String,
    val type: String = "text", // text | textarea | select | checkbox
    val required: Boolean = false,
    val options: List<String>? = null,
)

@Serializable
data class ActivityDto(
    val id: Long,
    val title: String,
    /** Legacy field: plain-text overview on current backends, rich-text HTML on older ones. */
    val description: String? = null,
    /** Short plain-text overview for cards (absent on older backends - fall back to [description]). */
    val overview: String? = null,
    /** Sanitised rich-text HTML for the detail screen (may carry `<img>`s); null when the admin left it empty. */
    @SerialName("full_description") val fullDescription: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    val location: String? = null,
    @SerialName("spots_left") val spotsLeft: Int? = null, // null = unlimited capacity
    @SerialName("is_open") val isOpen: Boolean,
    @SerialName("my_status") val myStatus: String, // registered | waitlisted | none
    @SerialName("waitlist_position") val waitlistPosition: Int? = null,
    val questions: List<QuestionDto> = emptyList(),
)

/** POST /api/v1/activities/{id}/register body. Keys are question keys; empty when there are no questions. */
@Serializable
data class RegisterActivityRequest(
    val answers: Map<String, String> = emptyMap(),
)
