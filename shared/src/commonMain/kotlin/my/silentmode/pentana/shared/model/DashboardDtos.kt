package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Aggregate for GET /api/v1/dashboard - one round-trip summary across the member's domains. */
@Serializable
data class DashboardDto(
    val bills: DashboardBillsDto,
    @SerialName("next_lunch") val nextLunch: DashboardLunchDto? = null,
    /** The other lunch in the window, shown under Coming up when [nextLunch] is a to-do. */
    @SerialName("other_lunch") val otherLunch: DashboardLunchDto? = null,
    @SerialName("rejected_proof") val rejectedProof: DashboardRejectedProofDto? = null,
    @SerialName("support_reply_ticket") val supportReplyTicket: DashboardSupportTicketDto? = null,
    @SerialName("next_activity") val nextActivity: DashboardActivityDto? = null,
    @SerialName("open_activities_count") val openActivitiesCount: Int = 0,
    @SerialName("pending_proofs_count") val pendingProofsCount: Int = 0,
    /** Tickets where the committee replied and the member hasn't yet: the Ask the committee card count. */
    @SerialName("support_replies_waiting") val supportRepliesWaiting: Int = 0,
)

@Serializable
data class DashboardBillsDto(
    @SerialName("total_outstanding") val totalOutstanding: String,
    @SerialName("available_credit") val availableCredit: String,
    @SerialName("unpaid_count") val unpaidCount: Int,
    /** Sum of proofs awaiting approval; "0.00" from an older server. */
    @SerialName("pending_proofs_amount") val pendingProofsAmount: String = "0.00",
)

@Serializable
data class DashboardMenuDto(
    val caterer: String? = null,
    val menu: String? = null,
)

@Serializable
data class DashboardLunchDto(
    val id: Long,
    val date: String,
    val caterer: String? = null,
    val menu: String? = null,
    @SerialName("is_open") val isOpen: Boolean,
    val responded: Boolean,
    val menus: List<DashboardMenuDto> = emptyList(),
    @SerialName("vote_deadline") val voteDeadline: String? = null,
)

@Serializable
data class DashboardActivityDto(
    val id: Long,
    val title: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("my_status") val myStatus: String,
)

@Serializable
data class DashboardRejectedProofDto(
    val id: Long,
    @SerialName("amount_claimed") val amountClaimed: String,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("rejected_at") val rejectedAt: String? = null,
)

@Serializable
data class DashboardSupportTicketDto(
    val id: Long,
    val subject: String,
)
