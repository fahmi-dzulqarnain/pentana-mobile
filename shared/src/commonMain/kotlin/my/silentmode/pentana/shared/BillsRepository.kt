package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.PaymentProofDto

class BillsRepository(private val client: ApiClient) {

    suspend fun bills(): List<BillDto> {
        val response = authedGet("/bills")
        return response.body<DataEnvelope<List<BillDto>>>().data
    }

    suspend fun summary(): BillsSummaryDto {
        val response = authedGet("/bills/summary")
        return response.body<DataEnvelope<BillsSummaryDto>>().data
    }

    suspend fun paymentProofs(): List<PaymentProofDto> {
        val response = authedGet("/payment-proofs")
        return response.body<DataEnvelope<List<PaymentProofDto>>>().data
    }

    /** Submit a payment proof (multipart). [imageBytes] is the raw JPEG/PNG bytes. */
    suspend fun submitPaymentProof(
        imageBytes: ByteArray,
        fileName: String,
        amountClaimed: String,
        memberNote: String?,
    ): PaymentProofDto {
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
        return response.body<DataEnvelope<PaymentProofDto>>().data
    }

    private suspend fun authedGet(path: String) = run {
        val token = client.tokenStore.get()
        client.http.get(client.urlFor(path)) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.also { client.ensureSuccess(it) }
    }
}
