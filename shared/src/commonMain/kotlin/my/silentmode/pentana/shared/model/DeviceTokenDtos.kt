package my.silentmode.pentana.shared.model

import kotlinx.serialization.Serializable

/** POST /api/v1/device-tokens body — registers an APNs token for push. */
@Serializable
data class DeviceTokenRequest(
    val token: String,
    val platform: String = "ios",
)
