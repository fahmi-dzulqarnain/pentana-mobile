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
import kotlin.test.assertTrue

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

    @Test
    fun activitiesAreParsedWithQuestions() = runTest {
        val engine = jsonEngine(
            """{"data":[{"id":7,"title":"Beach Cleanup","description":"<p>Bring gloves</p>","starts_at":"2026-07-01T09:00:00+08:00","location":"Beach","spots_left":5,"is_open":true,"my_status":"none","waitlist_position":null,"questions":[{"key":"diet","label":"Dietary needs","type":"text","required":true,"options":[]}]}]}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val activities = ActivitiesRepository(client).activities()

        assertEquals(1, activities.size)
        assertEquals("Beach Cleanup", activities[0].title)
        assertEquals("none", activities[0].myStatus)
        assertEquals(5, activities[0].spotsLeft)
        assertEquals("Dietary needs", activities[0].questions[0].label)
        assertEquals(true, activities[0].questions[0].required)
    }

    @Test
    fun registerSendsAnswersAndReturnsWaitlistStatus() = runTest {
        val engine = jsonEngine(
            """{"data":{"id":7,"title":"Beach Cleanup","is_open":true,"my_status":"waitlisted","waitlist_position":2,"questions":[]}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val activity = ActivitiesRepository(client).register(7, mapOf("diet" to "Halal"))

        assertEquals("waitlisted", activity.myStatus)
        assertEquals(2, activity.waitlistPosition)
    }

    @Test
    fun cancelReturnsTheUpdatedActivity() = runTest {
        val engine = jsonEngine(
            """{"data":{"id":7,"title":"Beach Cleanup","is_open":true,"my_status":"none","questions":[]}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val activity = ActivitiesRepository(client).cancel(7)

        assertEquals("none", activity.myStatus)
    }

    @Test
    fun dashboardIsParsed() = runTest {
        val engine = jsonEngine(
            """{"data":{"bills":{"total_outstanding":"70.00","available_credit":"20.00","unpaid_count":1},"next_lunch":{"id":2,"date":"2026-06-29","caterer":"ACME","menu":"Set A","is_open":true,"responded":false},"next_activity":{"id":7,"title":"Hiking","starts_at":"2026-07-01T09:00:00+08:00","my_status":"registered"},"open_activities_count":3,"pending_proofs_count":1}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val dash = DashboardRepository(client).dashboard()

        assertEquals("70.00", dash.bills.totalOutstanding)
        assertEquals(1, dash.bills.unpaidCount)
        assertEquals(2L, dash.nextLunch?.id)
        assertEquals("Set A", dash.nextLunch?.menu)
        assertEquals(false, dash.nextLunch?.responded)
        assertEquals("registered", dash.nextActivity?.myStatus)
        assertEquals(3, dash.openActivitiesCount)
        assertEquals(1, dash.pendingProofsCount)
    }

    @Test
    fun dashboardEmptyStateParses() = runTest {
        val engine = jsonEngine(
            """{"data":{"bills":{"total_outstanding":"0.00","available_credit":"0.00","unpaid_count":0},"next_lunch":null,"next_activity":null,"open_activities_count":0,"pending_proofs_count":0}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val dash = DashboardRepository(client).dashboard()

        assertEquals(null, dash.nextLunch)
        assertEquals(null, dash.nextActivity)
        assertEquals(0, dash.openActivitiesCount)
    }

    @Test
    fun notificationsAreParsedWithUnreadCount() = runTest {
        val engine = jsonEngine(
            """{"data":[{"id":"abc","title":"Lunch published: Nasi Lemak","body":"Order by 12:00","read":false,"created_at":"2026-06-27T09:00:00+08:00"},{"id":"def","title":"Old","body":null,"read":true,"created_at":null}],"unread_count":1}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val page = NotificationsRepository(client).notifications()

        assertEquals(2, page.data.size)
        assertEquals(1, page.unreadCount)
        assertEquals("Lunch published: Nasi Lemak", page.data[0].title)
        assertEquals(false, page.data[0].read)
    }

    @Test
    fun markAllReadReturnsZero() = runTest {
        val engine = jsonEngine("""{"unread_count":0}""")
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val remaining = NotificationsRepository(client).markAllRead()

        assertEquals(0, remaining)
    }

    @Test
    fun passkeyLoginOptionsReturnsStateAndPublicKey() = runTest {
        val engine = jsonEngine(
            """{"state":"abc123","publicKey":{"challenge":"Y2hhbGxlbmdl","rpId":"pentana.silentmode.my","allowCredentials":[]}}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore(), engine)

        val challenge = PasskeyRepository(client).loginOptions()

        assertEquals("abc123", challenge.state)
        assertTrue(challenge.publicKeyJson.contains("pentana.silentmode.my"))
    }

    @Test
    fun passkeyLoginVerifyStoresTokenAndReturnsUser() = runTest {
        val engine = jsonEngine(
            """{"token":"pk-tok","user":{"id":1,"name":"Aisyah","email":"a@org.com","credit":0}}""",
        )
        val store = InMemoryTokenStore()
        val client = ApiClient("https://example.test/api/v1", store, engine)

        val user = PasskeyRepository(client).loginVerify("abc123", """{"id":"x","type":"public-key","response":{}}""")

        assertEquals("Aisyah", user.name)
        assertEquals("pk-tok", store.get())
    }

    @Test
    fun passkeyListIsParsed() = runTest {
        val engine = jsonEngine(
            """{"data":[{"id":5,"name":"iPhone","last_used_at":null,"created_at":"2026-06-27T00:00:00+00:00"}]}""",
        )
        val client = ApiClient("https://example.test/api/v1", InMemoryTokenStore("tok"), engine)

        val list = PasskeyRepository(client).list()

        assertEquals(1, list.size)
        assertEquals("iPhone", list[0].name)
    }
}
