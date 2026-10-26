package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A support ticket as the member's list shows it. [status] is the server's raw value
 * (needs_reply / waiting_on_member / closed); presentation/SupportDisplay.kt words it.
 */
@Serializable
data class SupportTicketDto(
    val id: Long,
    val type: String,
    val subject: String,
    val status: String,
    @SerialName("last_message_at") val lastMessageAt: String,
    @SerialName("last_message_from_committee") val lastMessageFromCommittee: Boolean = false,
)

/** One ticket with its conversation. Who closed it is only "the committee or you", never which admin. */
@Serializable
data class SupportTicketDetailDto(
    val id: Long,
    val type: String,
    val subject: String,
    val status: String,
    @SerialName("last_message_at") val lastMessageAt: String,
    @SerialName("last_message_from_committee") val lastMessageFromCommittee: Boolean = false,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("closed_by_committee") val closedByCommittee: Boolean = false,
    val messages: List<SupportMessageDto> = emptyList(),
)

@Serializable
data class SupportMessageDto(
    val id: Long,
    val body: String,
    @SerialName("from_committee") val fromCommittee: Boolean,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("created_at") val createdAt: String,
    /** Token-authenticated URL of the screenshot; fetch it with SupportRepository.attachment(). */
    @SerialName("attachment_url") val attachmentUrl: String? = null,
)
