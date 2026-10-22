package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import my.silentmode.pentana.shared.model.PaymentProofDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillsRepositoryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val json = Json { ignoreUnknownKeys = true }
    private val pdfBytes = "%PDF-1.7 receipt".encodeToByteArray()

    private fun repositoryRespondingWith(
        status: HttpStatusCode,
        contentDisposition: String? = null,
        contentType: String = "application/pdf",
        onRequest: (HttpRequestData) -> Unit = {},
    ): BillsRepository {
        val engine = MockEngine { request ->
            onRequest(request)
            val headerPairs = buildList {
                add(HttpHeaders.ContentType to listOf(contentType))
                contentDisposition?.let { add(HttpHeaders.ContentDisposition to listOf(it)) }
            }
            respond(pdfBytes, status, headersOf(*headerPairs.toTypedArray()))
        }
        return BillsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine))
    }

    @Test fun proof_decodes_receipt_fields() {
        val proof = json.decodeFromString<PaymentProofDto>(
            """{"id":11,"amount_claimed":"50.00","status":"verified","receipt_number":"PEN-2026-0001","has_receipt":true}""",
        )
        assertEquals("PEN-2026-0001", proof.receiptNumber)
        assertTrue(proof.hasReceipt)
    }

    @Test fun proof_without_receipt_fields_defaults_to_no_receipt() {
        val proof = json.decodeFromString<PaymentProofDto>("""{"id":11,"amount_claimed":"50.00","status":"verified"}""")
        assertNull(proof.receiptNumber)
        assertFalse(proof.hasReceipt)
    }

    @Test fun receipt_download_sends_token_and_reads_file_name() = runTest {
        var seenRequest: HttpRequestData? = null
        val repository = repositoryRespondingWith(
            HttpStatusCode.OK,
            contentDisposition = "attachment; filename=\"PEN-2026-0001.pdf\"",
            onRequest = { seenRequest = it },
        )
        val receipt = repository.receipt(11)
        assertEquals("/api/v1/payment-proofs/11/receipt", seenRequest?.url?.encodedPath)
        assertEquals("Bearer t", seenRequest?.headers?.get(HttpHeaders.Authorization))
        assertEquals("PEN-2026-0001.pdf", receipt.fileName)
        assertContentEquals(pdfBytes, receipt.bytes)
    }

    @Test fun receipt_404_throws_api_exception() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.NotFound)
        val failure = assertFailsWith<ApiException> { repository.receipt(11) }
        assertEquals(404, failure.status)
    }

    @Test fun receipt_file_name_is_sanitised() {
        assertEquals("receipt-11.pdf", receiptFileName(null, proofId = 11))
        assertEquals("receipt-11.pdf", receiptFileName("attachment", proofId = 11))
        assertEquals("evil.pdf", receiptFileName("attachment; filename=\"../evil.pdf\"", proofId = 11))
        assertEquals("PEN-2026-0001.pdf", receiptFileName("attachment; filename=\"PEN-2026-0001\"", proofId = 11))
        assertEquals("PEN-2026-0002.pdf", receiptFileName("attachment; filename=PEN-2026-0002.pdf", proofId = 11))
    }

    @Test fun receipt_file_name_is_capped_in_length() {
        val longName = "A".repeat(300)
        val fileName = receiptFileName("attachment; filename=\"$longName.pdf\"", proofId = 11)
        assertEquals("A".repeat(100) + ".pdf", fileName)
    }

    /** Laravel only answers JSON (401/404) instead of redirecting to the web login when JSON comes first. */
    @Test fun receipt_request_asks_for_json_errors_first() = runTest {
        var seenRequest: HttpRequestData? = null
        val repository = repositoryRespondingWith(HttpStatusCode.OK, onRequest = { seenRequest = it })
        repository.receipt(11)
        assertEquals("application/json, application/pdf", seenRequest?.headers?.get(HttpHeaders.Accept))
    }

    /** A followed redirect lands on an HTML page with 200; that must not be saved as the receipt. */
    @Test fun receipt_non_pdf_response_throws() = runTest {
        val repository = repositoryRespondingWith(HttpStatusCode.OK, contentType = "text/html; charset=utf-8")
        assertFailsWith<IllegalStateException> { repository.receipt(11) }
    }
}
