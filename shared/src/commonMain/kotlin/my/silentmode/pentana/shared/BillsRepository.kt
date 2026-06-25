package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.PaymentProofDto

class BillsRepository(private val client: ApiClient) {

    suspend fun bills(): List<BillDto> = withContext(Dispatchers.Main) {
        authedGet("/bills").body<DataEnvelope<List<BillDto>>>().data
    }

    suspend fun summary(): BillsSummaryDto = withContext(Dispatchers.Main) {
        authedGet("/bills/summary").body<DataEnvelope<BillsSummaryDto>>().data
    }

    suspend fun paymentProofs(): List<PaymentProofDto> = withContext(Dispatchers.Main) {
        authedGet("/payment-proofs").body<DataEnvelope<List<PaymentProofDto>>>().data
    }

    /** Submit a payment proof (multipart). [imageBytes] is the raw JPEG/PNG bytes. */
    suspend fun submitPaymentProof(
        imageBytes: ByteArray,
        fileName: String,
        amountClaimed: String,
        memberNote: String?,
    ): PaymentProofDto = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.post(client.urlFor("/payment-proofs")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("amount_claimed", amountClaimed)
                        memberNote?.let { append("member_note", it) }
                        append(
                            "proof_image",
                            imageBytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<PaymentProofDto>>().data
    }

    private suspend fun authedGet(path: String): HttpResponse {
        val token = client.tokenStore.get()
        return client.http.get(client.urlFor(path)) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.also { client.ensureSuccess(it) }
    }
}
