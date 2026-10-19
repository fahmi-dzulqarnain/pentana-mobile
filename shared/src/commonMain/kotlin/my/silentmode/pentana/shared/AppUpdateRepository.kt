package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.AndroidReleaseDto

/**
 * Checks for a newer sideloaded Android build. There is no Play Store listing, so the
 * app has to ask the server; the endpoint is public and needs no token.
 */
class AppUpdateRepository(private val client: ApiClient) {

    /** The newest published release, or null when none has been published yet (404). */
    @Throws(Exception::class)
    suspend fun latestAndroidRelease(): AndroidReleaseDto? = withContext(Dispatchers.Main) {
        val response = client.http.get(client.urlFor("/android/latest")) {
            header(HttpHeaders.Accept, "application/json")
        }
        if (response.status == HttpStatusCode.NotFound) return@withContext null
        client.ensureSuccess(response)
        response.body<AndroidReleaseDto>()
    }

    /** The release worth offering to an app at [installedVersionCode], or null when it is already current. */
    @Throws(Exception::class)
    suspend fun availableUpdate(installedVersionCode: Int): AndroidReleaseDto? =
        latestAndroidRelease()?.takeIf { it.versionCode > installedVersionCode }
}
