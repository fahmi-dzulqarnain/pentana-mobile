package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Login request body for POST /api/v1/login. */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

/** POST /api/v1/login response: { token, user }. */
@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    @SerialName("member_category") val memberCategory: String? = null,
    val birthday: String? = null,
    // UserResource returns credit as a JSON number.
    val credit: Double = 0.0,
    // Defaults keep older servers (no nickname support) decoding fine.
    val nickname: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

/** Greeting name: the nickname if set, otherwise the first word of the full name. */
val UserDto.greetingName: String
    get() = nickname ?: name.trim().substringBefore(' ')

/** Input for avatar initials: the nickname if set, otherwise the full name. */
val UserDto.avatarName: String
    get() = nickname ?: name

/**
 * Money fields are fixed-2dp strings from the API (e.g. "70.00") to avoid float drift -
 * parse to a Decimal/BigDecimal type on the UI side if you need arithmetic.
 */
@Serializable
data class BillDto(
    val id: Long,
    val month: String,
    @SerialName("amount_due") val amountDue: String,
    @SerialName("amount_paid") val amountPaid: String,
    val outstanding: String,
    val status: String,
)

@Serializable
data class BillsSummaryDto(
    @SerialName("total_outstanding") val totalOutstanding: String,
    @SerialName("available_credit") val availableCredit: String,
    @SerialName("unpaid_count") val unpaidCount: Int,
    @SerialName("bill_count") val billCount: Int,
)

@Serializable
data class PaymentProofDto(
    val id: Long,
    @SerialName("amount_claimed") val amountClaimed: String,
    @SerialName("amount_verified") val amountVerified: String? = null,
    val status: String,
    @SerialName("member_note") val memberNote: String? = null,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("proof_image_url") val proofImageUrl: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    /** "PEN-2026-0001" once a verification issued a receipt; null before launch. */
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("has_receipt") val hasReceipt: Boolean = false,
)

/** A downloaded paid-receipt PDF, ready to hand to the platform viewer. */
class ReceiptPdf(val fileName: String, val bytes: ByteArray)

/** API resources are wrapped as { "data": ... }. */
@Serializable
data class DataEnvelope<T>(val data: T)
