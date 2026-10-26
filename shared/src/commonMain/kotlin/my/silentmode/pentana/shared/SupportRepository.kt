package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.SupportMessageDto
import my.silentmode.pentana.shared.model.SupportTicketDetailDto
import my.silentmode.pentana.shared.model.SupportTicketDto

/** The member's "Ask the committee" tickets (api/v1/support-tickets). Members only: admins get 403. */
class SupportRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun tickets(): List<SupportTicketDto> = withContext(Dispatchers.Main) {
        authedGet("/support-tickets").body<DataEnvelope<List<SupportTicketDto>>>().data
    }

    @Throws(Exception::class)
    suspend fun ticket(id: Long): SupportTicketDetailDto = withContext(Dispatchers.Main) {
        authedGet("/support-tickets/$id").body<DataEnvelope<SupportTicketDetailDto>>().data
    }

    /** Open a ticket (multipart). [screenshot] is JPEG bytes, or null for none. */
    @Throws(Exception::class)
    suspend fun open(
        type: String,
        subject: String,
        body: String,
        screenshot: ByteArray?,
        fileName: String?,
    ): SupportTicketDetailDto = withContext(Dispatchers.Main) {
        val response = client.http.post(client.urlFor("/support-tickets")) {
            authHeaders()
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("type", type)
                        append("subject", subject)
                        append("body", body)
                        screenshot?.let {
                            append(
                                "screenshot",
                                it,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName ?: "screenshot.jpg"}\"")
                                },
                            )
                        }
                    },
                ),
            )
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<SupportTicketDetailDto>>().data
    }

    @Throws(Exception::class)
    suspend fun reply(id: Long, body: String): SupportMessageDto = withContext(Dispatchers.Main) {
        val response = client.http.post(client.urlFor("/support-tickets/$id/replies")) {
            authHeaders()
            contentType(ContentType.Application.Json)
            setBody(ReplyBody(body))
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<SupportMessageDto>>().data
    }

    @Throws(Exception::class)
    suspend fun resolve(id: Long): SupportTicketDetailDto = withContext(Dispatchers.Main) {
        val response = client.http.post(client.urlFor("/support-tickets/$id/resolve")) { authHeaders() }
        client.ensureSuccess(response)
        response.body<DataEnvelope<SupportTicketDetailDto>>().data
    }

    /** A message's screenshot. [url] is the absolute attachment_url from the API, which needs the token. */
    @Throws(Exception::class)
    suspend fun attachment(url: String): ByteArray = withContext(Dispatchers.Main) {
        val response = client.http.get(url) {
            // JSON first so an expired token is a JSON 401, not a redirect to the web login.
            header(HttpHeaders.Accept, "application/json, image/*")
            client.tokenStore.get()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.readRawBytes()
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authHeaders() {
        header(HttpHeaders.Accept, "application/json")
        client.tokenStore.get()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun authedGet(path: String): HttpResponse {
        val token = client.tokenStore.get()
        return client.http.get(client.urlFor(path)) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.also { client.ensureSuccess(it) }
    }

    @Serializable
    private data class ReplyBody(val body: String)
}
