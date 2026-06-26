package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PentanaApiTest {

    // The repos hop to Dispatchers.Main (correct for the iOS app); install a test Main here.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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

    @Test
    fun lunchesAreParsed() = runTest {
        val engine = jsonEngine(
            """{"data":[{"id":1,"date":"2026-06-28","caterer":"ACME","menu":"Set A","deadline":"2026-06-27T12:00:00+08:00","is_open":true,"options":[{"meal_option_id":5,"name":"Beef"}],"responded":false,"my_meal_option_id":null}]}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val lunches = LunchRepository(client).lunches()

        assertEquals(1, lunches.size)
        assertEquals(true, lunches[0].isOpen)
        assertEquals("Beef", lunches[0].options[0].name)
        assertEquals(5L, lunches[0].options[0].mealOptionId)
    }

    @Test
    fun respondReturnsTheUpdatedLunch() = runTest {
        val engine = jsonEngine(
            """{"data":{"id":1,"date":"2026-06-28","is_open":true,"options":[],"responded":true,"my_meal_option_id":5}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val lunch = LunchRepository(client).respond(1, 5)

        assertEquals(true, lunch.responded)
        assertEquals(5L, lunch.myMealOptionId)
    }
}
