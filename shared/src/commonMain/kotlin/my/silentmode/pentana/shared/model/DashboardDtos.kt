package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Aggregate for GET /api/v1/dashboard — one round-trip summary across the member's domains. */
@Serializable
data class DashboardDto(
    val bills: DashboardBillsDto,
    @SerialName("next_lunch") val nextLunch: DashboardLunchDto? = null,
    @SerialName("next_activity") val nextActivity: DashboardActivityDto? = null,
    @SerialName("open_activities_count") val openActivitiesCount: Int = 0,
    @SerialName("pending_proofs_count") val pendingProofsCount: Int = 0,
)

@Serializable
data class DashboardBillsDto(
    @SerialName("total_outstanding") val totalOutstanding: String,
    @SerialName("available_credit") val availableCredit: String,
    @SerialName("unpaid_count") val unpaidCount: Int,
)

@Serializable
data class DashboardLunchDto(
    val id: Long,
    val date: String,
    @SerialName("is_open") val isOpen: Boolean,
    val responded: Boolean,
)

@Serializable
data class DashboardActivityDto(
    val id: Long,
    val title: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("my_status") val myStatus: String,
)
