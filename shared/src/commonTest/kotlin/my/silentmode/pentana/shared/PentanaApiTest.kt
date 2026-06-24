package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PentanaApiTest {

    private fun jsonEngine(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun loginStoresTheTokenAndReturnsTheUser() = runTest {
        val engine = jsonEngine(
            """{"token":"tok123","user":{"id":1,"name":"Aisyah","email":"a@org.com","member_category":"Grade 12-14","birthday":"1990-06-23","credit":0}}""",
        )
        val store = InMemoryTokenStore()
        val client = ApiClient("https://example.test/api/v1", store, engine)

        val user = AuthRepository(client).login("a@org.com", "secret", "iPhone")

        assertEquals("Aisyah", user.name)
        assertEquals("tok123", store.get())
    }

    @Test
    fun billsAreParsedFromTheDataEnvelope() = runTest {
        val engine = jsonEngine(
            """{"data":[{"id":1,"month":"2026-06","amount_due":"100.00","amount_paid":"30.00","outstanding":"70.00","status":"partial"}]}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val bills = BillsRepository(client).bills()

        assertEquals(1, bills.size)
        assertEquals("70.00", bills[0].outstanding)
        assertEquals("partial", bills[0].status)
    }

    @Test
    fun summaryIsParsed() = runTest {
        val engine = jsonEngine(
            """{"data":{"total_outstanding":"120.00","available_credit":"0.00","unpaid_count":2,"bill_count":2}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val summary = BillsRepository(client).summary()

        assertEquals("120.00", summary.totalOutstanding)
        assertEquals(2, summary.billCount)
    }

    @Test
    fun aFailureStatusRaisesApiException() = runTest {
        val engine = jsonEngine("""{"message":"Unauthorized"}""", HttpStatusCode.Unauthorized)
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        try {
            BillsRepository(client).bills()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
    }
}
