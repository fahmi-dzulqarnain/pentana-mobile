package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A registered passkey, for the Profile list. */
@Serializable
data class PasskeyDto(
    val id: Long,
    val name: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
