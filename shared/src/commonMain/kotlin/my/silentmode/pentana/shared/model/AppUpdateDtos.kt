package my.silentmode.pentana.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The newest sideloaded Android build, from `GET /android/latest`. */
@Serializable
data class AndroidReleaseDto(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    val size: Long,
    val sha256: String,
    val notes: String? = null,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
)
