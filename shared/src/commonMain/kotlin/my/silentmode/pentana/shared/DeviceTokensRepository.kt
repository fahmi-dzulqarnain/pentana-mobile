package my.silentmode.pentana.shared

import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.DeviceTokenRequest

/** Registers/unregisters the device's APNs token with the backend. The OS-level
 *  remote-notification registration that produces the token is iOS (Swift). */
class DeviceTokensRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun register(token: String, platform: String = "ios"): Unit = withContext(Dispatchers.Main) {
        val auth = client.tokenStore.get()
        val response = client.http.post(client.urlFor("/device-tokens")) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json")
            auth?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(DeviceTokenRequest(token, platform))
        }
        client.ensureSuccess(response)
    }

    @Throws(Exception::class)
    suspend fun unregister(token: String): Unit = withContext(Dispatchers.Main) {
        val auth = client.tokenStore.get()
        val response = client.http.delete(client.urlFor("/device-tokens/$token")) {
            header(HttpHeaders.Accept, "application/json")
            auth?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
    }
}
