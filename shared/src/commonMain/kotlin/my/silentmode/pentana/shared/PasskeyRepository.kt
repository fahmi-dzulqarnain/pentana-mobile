package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.LoginResponse
import my.silentmode.pentana.shared.model.PasskeyDto
import my.silentmode.pentana.shared.model.UserDto

/**
 * WebAuthn options + the `state` token to echo back on verify. `publicKeyJson` is
 * the raw options object as a JSON string, which the iOS layer parses to drive
 * AuthenticationServices (the OS ceremony can't live in shared Kotlin).
 */
data class PasskeyChallenge(val state: String, val publicKeyJson: String)

class PasskeyRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun loginOptions(): PasskeyChallenge = options("/passkeys/login/options", authed = false)

    /** Verifies a sign-in assertion, persists the returned token, returns the member. */
    @Throws(Exception::class)
    suspend fun loginVerify(state: String, credentialJson: String): UserDto = withContext(Dispatchers.Main) {
        val response = client.http.post(client.urlFor("/passkeys/login/verify")) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json")
            setBody(verifyBody(state, credentialJson, null))
        }
        client.ensureSuccess(response)
        val body = response.body<LoginResponse>()
        client.tokenStore.save(body.token)
        body.user
    }

    @Throws(Exception::class)
    suspend fun registerOptions(): PasskeyChallenge = options("/passkeys/register/options", authed = true)

    @Throws(Exception::class)
    suspend fun registerVerify(state: String, credentialJson: String, name: String?): Unit =
        withContext(Dispatchers.Main) {
            val token = client.tokenStore.get()
            val response = client.http.post(client.urlFor("/passkeys/register/verify")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json")
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(verifyBody(state, credentialJson, name))
            }
            client.ensureSuccess(response)
        }

    @Throws(Exception::class)
    suspend fun list(): List<PasskeyDto> = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.get(client.urlFor("/passkeys")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<List<PasskeyDto>>>().data
    }

    @Throws(Exception::class)
    suspend fun delete(id: Long): Unit = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.delete(client.urlFor("/passkeys/$id")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
    }

    private suspend fun options(path: String, authed: Boolean): PasskeyChallenge = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.post(client.urlFor(path)) {
            header(HttpHeaders.Accept, "application/json")
            if (authed) token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val state = root["state"]!!.jsonPrimitive.content
        val publicKey = root["publicKey"]!!.jsonObject
        PasskeyChallenge(state, json.encodeToString(JsonObject.serializer(), publicKey))
    }

    private fun verifyBody(state: String, credentialJson: String, name: String?): JsonObject = buildJsonObject {
        put("state", state)
        put("credential", json.parseToJsonElement(credentialJson))
        if (name != null) put("name", name)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
