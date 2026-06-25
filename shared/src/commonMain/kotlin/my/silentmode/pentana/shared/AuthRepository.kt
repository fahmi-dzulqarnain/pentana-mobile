package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.LoginRequest
import my.silentmode.pentana.shared.model.LoginResponse
import my.silentmode.pentana.shared.model.UserDto

class AuthRepository(private val client: ApiClient) {

    /** Logs in, persists the returned token, and returns the member profile. */
    suspend fun login(email: String, password: String, deviceName: String): UserDto =
        withContext(Dispatchers.Default) {
            val response = client.http.post(client.urlFor("/login")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json")
                setBody(LoginRequest(email, password, deviceName))
            }
            client.ensureSuccess(response)

            val body: LoginResponse = response.body()
            client.tokenStore.save(body.token)
            body.user
        }

    suspend fun me(): UserDto = withContext(Dispatchers.Default) {
        val token = client.tokenStore.get()
        val response = client.http.get(client.urlFor("/me")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<UserDto>>().data
    }

    suspend fun logout(): Unit = withContext(Dispatchers.Default) {
        val token = client.tokenStore.get()
        client.http.post(client.urlFor("/logout")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.tokenStore.clear()
    }

    /** True if a token is already stored (auto-login). */
    fun isLoggedIn(): Boolean = client.tokenStore.get() != null
}
