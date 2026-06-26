package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String, // UUID
    val title: String,
    val body: String? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/** GET /api/v1/notifications response: the list plus the unread count for the bell badge. */
@Serializable
data class NotificationsPageDto(
    val data: List<NotificationDto> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)
