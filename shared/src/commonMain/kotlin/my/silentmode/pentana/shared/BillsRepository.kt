package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.statement.readRawBytes
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
import my.silentmode.pentana.shared.model.ReceiptPdf

class BillsRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun bills(): List<BillDto> = withContext(Dispatchers.Main) {
        authedGet("/bills").body<DataEnvelope<List<BillDto>>>().data
    }

    @Throws(Exception::class)
    suspend fun summary(): BillsSummaryDto = withContext(Dispatchers.Main) {
        authedGet("/bills/summary").body<DataEnvelope<BillsSummaryDto>>().data
    }

    @Throws(Exception::class)
    suspend fun paymentProofs(): List<PaymentProofDto> = withContext(Dispatchers.Main) {
        authedGet("/payment-proofs").body<DataEnvelope<List<PaymentProofDto>>>().data
    }

    /** Submit a payment proof (multipart). [imageBytes] is the raw JPEG/PNG bytes. */
    @Throws(Exception::class)
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

    /** The paid-receipt PDF for one of the member's verified proofs. 404 when it isn't theirs or has no receipt. */
    @Throws(Exception::class)
    suspend fun receipt(proofId: Long): ReceiptPdf = withContext(Dispatchers.Main) {
        val response = authedGet("/payment-proofs/$proofId/receipt", accept = "application/pdf")
        ReceiptPdf(receiptFileName(response.headers[HttpHeaders.ContentDisposition], proofId), response.readRawBytes())
    }

    private suspend fun authedGet(path: String, accept: String = "application/json"): HttpResponse {
        val token = client.tokenStore.get()
        return client.http.get(client.urlFor(path)) {
            header(HttpHeaders.Accept, accept)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.also { client.ensureSuccess(it) }
    }
}

/**
 * A safe file name for the downloaded receipt: the server's Content-Disposition filename reduced to a
 * plain basename ending in .pdf, or "receipt-<id>.pdf" when the header gives nothing usable.
 */
internal fun receiptFileName(contentDisposition: String?, proofId: Long): String {
    val fallback = "receipt-$proofId.pdf"
    val rawName = contentDisposition
        ?.let { Regex("filename=\"?([^\";]+)\"?").find(it)?.groupValues?.get(1) }
        ?: return fallback
    val baseName = rawName.substringAfterLast('/').substringAfterLast('\\')
        .filter { character -> character.isLetterOrDigit() || character in "._-" }
        .trimStart('.')
    val stem = (if (baseName.endsWith(".pdf", ignoreCase = true)) baseName.dropLast(4) else baseName)
        .take(MAX_RECEIPT_STEM_LENGTH)
    return if (stem.isEmpty()) fallback else "$stem.pdf"
}

/** File systems cap names at 255 bytes; a receipt number is ~13 characters, so 100 is generous. */
private const val MAX_RECEIPT_STEM_LENGTH = 100
