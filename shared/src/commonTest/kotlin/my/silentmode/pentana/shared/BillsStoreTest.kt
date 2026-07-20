package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.presentation.BillStatus
import my.silentmode.pentana.shared.presentation.BillsStore
import my.silentmode.pentana.shared.presentation.BillsUiState
import my.silentmode.pentana.shared.presentation.SubmitState
import my.silentmode.pentana.shared.presentation.billStatus
import my.silentmode.pentana.shared.presentation.canSubmitProof
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BillsStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val summaryJson = """
        {"data":{"total_outstanding":"120.00","available_credit":"15.00","unpaid_count":2,"bill_count":6}}
    """.trimIndent()

    private val billsJson = """
        {"data":[
        {"id":1,"month":"2026-06","amount_due":"70.00","amount_paid":"0.00","outstanding":"70.00","status":"overdue"},
        {"id":2,"month":"2026-07","amount_due":"70.00","amount_paid":"20.00","outstanding":"50.00","status":"partial"}
        ]}
    """.trimIndent()

    private val proofJson = """
        {"data":{"id":9,"amount_claimed":"50.00","status":"pending","submitted_at":"2026-07-19T10:00:00+08:00"}}
    """.trimIndent()

    private fun makeStore(handler: (String) -> Pair<HttpStatusCode, String>): BillsStore {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return BillsStore(BillsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    private fun routing(proofResult: Pair<HttpStatusCode, String>? = null): (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/bills/summary") -> HttpStatusCode.OK to summaryJson
            path.endsWith("/bills") -> HttpStatusCode.OK to billsJson
            path.endsWith("/payment-proofs") -> proofResult ?: (HttpStatusCode.OK to proofJson)
            else -> HttpStatusCode.NotFound to "{}"
        }
    }

    @Test fun load_emits_content_with_summary_and_bills() = runTest {
        val store = makeStore(routing())
        val uiState = store.state.first { it !is BillsUiState.Loading }
        assertIs<BillsUiState.Content>(uiState)
        assertEquals("120.00", uiState.summary.totalOutstanding)
        assertEquals(2, uiState.bills.size)
        assertEquals("2026-06", uiState.bills.first().month)
    }

    @Test fun error_emits_error() = runTest {
        val store = makeStore { _ -> HttpStatusCode.InternalServerError to "{}" }
        val uiState = store.state.first { it !is BillsUiState.Loading }
        assertIs<BillsUiState.Error>(uiState)
        assertEquals("Something went wrong. Pull down to refresh.", uiState.message)
    }

    @Test fun submit_success_transitions_and_refetches() = runTest {
        var billsFetches = 0
        // Second fetch returns updated data so the test can await the refetch landing in state
        // (the refetch runs AFTER Success — Success alone doesn't prove the list was refetched).
        val refetchedBillsJson = billsJson.replace("\"outstanding\":\"70.00\"", "\"outstanding\":\"20.00\"")
        val store = makeStore { path ->
            when {
                path.endsWith("/bills/summary") -> HttpStatusCode.OK to summaryJson
                path.endsWith("/bills") -> {
                    billsFetches += 1
                    HttpStatusCode.OK to (if (billsFetches == 1) billsJson else refetchedBillsJson)
                }
                path.endsWith("/payment-proofs") -> HttpStatusCode.OK to proofJson
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        store.state.first { it is BillsUiState.Content }
        assertEquals(1, billsFetches)
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Success } // instant feedback: Success precedes the refetch
        store.state.first { it is BillsUiState.Content && it.bills.first().outstanding == "20.00" }
        assertEquals(2, billsFetches) // success refetches the list
    }

    @Test fun submit_failure_sets_error_state() = runTest {
        val store = makeStore(routing(proofResult = HttpStatusCode.InternalServerError to "{}"))
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        val submitState = store.submit.first { it is SubmitState.Error }
        assertIs<SubmitState.Error>(submitState)
        assertEquals("Upload failed. Please try again.", submitState.message)
    }

    @Test fun reset_returns_submit_to_idle() = runTest {
        val store = makeStore(routing(proofResult = HttpStatusCode.InternalServerError to "{}"))
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Error }
        store.resetSubmit()
        assertIs<SubmitState.Idle>(store.submit.value)
    }

    @Test fun submitting_guards_double_submit() = runTest {
        val gate = CompletableDeferred<Unit>()
        var proofRequests = 0
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/bills/summary") -> summaryJson
                path.endsWith("/payment-proofs") -> { proofRequests += 1; gate.await(); proofJson }
                else -> billsJson
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = BillsStore(BillsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Submitting }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "60.00", note = null) // guarded
        gate.complete(Unit)
        store.submit.first { it is SubmitState.Success }
        assertEquals(1, proofRequests)
    }

    @Test fun resubmit_after_error_is_allowed() = runTest {
        // Pins the retry path: the guard must check Submitting specifically, not "anything
        // other than Idle" — an Error state must not block a second attempt.
        var proofRequests = 0
        val store = makeStore { path ->
            when {
                path.endsWith("/bills/summary") -> HttpStatusCode.OK to summaryJson
                path.endsWith("/bills") -> HttpStatusCode.OK to billsJson
                path.endsWith("/payment-proofs") -> {
                    proofRequests += 1
                    if (proofRequests == 1) HttpStatusCode.InternalServerError to "{}" else HttpStatusCode.OK to proofJson
                }
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Error }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Success }
        assertEquals(2, proofRequests)
    }

    @Test fun invalid_amount_fires_no_request() = runTest {
        // Pins the silent precheck: a non-numeric amount never reaches the network and leaves
        // submit at Idle (the UI's canSubmitProof-gated button is the user-facing feedback).
        var proofRequests = 0
        val store = makeStore { path ->
            if (path.endsWith("/payment-proofs")) { proofRequests += 1; HttpStatusCode.OK to proofJson }
            else routing()(path)
        }
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "abc", note = null)
        assertEquals(0, proofRequests)
        assertIs<SubmitState.Idle>(store.submit.value)
    }

    @Test fun blank_note_and_padded_amount_still_submit() = runTest {
        var sawProofRequest = false
        val store = makeStore { path ->
            if (path.endsWith("/payment-proofs")) { sawProofRequest = true; HttpStatusCode.OK to proofJson }
            else routing()(path)
        }
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = " 50.00 ", note = "   ")
        store.submit.first { it is SubmitState.Success }
        assertTrue(sawProofRequest)
    }

    // Derived display

    @Test fun bill_status_maps_all_cases() {
        assertEquals(BillStatus.Paid, billStatus(bill(status = "paid")))
        assertEquals(BillStatus.Partial, billStatus(bill(status = "partial")))
        assertEquals(BillStatus.Overdue, billStatus(bill(status = "overdue")))
        assertEquals(BillStatus.Unpaid, billStatus(bill(status = "unpaid")))
        assertEquals(BillStatus.Unpaid, billStatus(bill(status = "anything-else")))
        assertEquals(BillStatus.Paid, billStatus(bill(status = "PAID"))) // case-insensitive, as both platforms did
    }

    @Test fun can_submit_requires_numeric_amount_and_photo() {
        assertTrue(canSubmitProof("50.00", hasPhoto = true))
        assertTrue(canSubmitProof(" 50.00 ", hasPhoto = true))
        assertFalse(canSubmitProof("50.00", hasPhoto = false))
        assertFalse(canSubmitProof("", hasPhoto = true))
        assertFalse(canSubmitProof("abc", hasPhoto = true)) // unified on iOS's stricter numeric check
        assertFalse(canSubmitProof("NaN", hasPhoto = true)) // parses as a Double, but not a currency amount
        assertFalse(canSubmitProof("Infinity", hasPhoto = true))
    }

    private fun bill(status: String) = BillDto(
        id = 1, month = "2026-06", amountDue = "70.00", amountPaid = "0.00", outstanding = "70.00", status = status,
    )
}
