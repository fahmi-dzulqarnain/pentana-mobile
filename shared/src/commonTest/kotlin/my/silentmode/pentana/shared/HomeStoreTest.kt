package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardBillsDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto
import my.silentmode.pentana.shared.presentation.DashboardActivityStatus
import my.silentmode.pentana.shared.presentation.HomeStore
import my.silentmode.pentana.shared.presentation.HomeUiState
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.dashboardActivityStatus
import my.silentmode.pentana.shared.presentation.dashboardLunchStatus
import my.silentmode.pentana.shared.presentation.duesCleared
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val dashboardJson = """
        {"data":{
        "bills":{"total_outstanding":"120.00","available_credit":"15.00","unpaid_count":2},
        "next_lunch":{"id":1,"date":"2026-07-20","caterer":"Dapur Selera","menu":"Nasi Lemak","is_open":true,"responded":false},
        "next_activity":{"id":5,"title":"Beach Cleanup","starts_at":"2026-07-25T08:00:00+08:00","my_status":"registered"},
        "open_activities_count":3,
        "pending_proofs_count":1}}
    """.trimIndent()

    private fun store(handler: () -> Pair<HttpStatusCode, String>): HomeStore {
        val engine = MockEngine { _ ->
            val (status, body) = handler()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HomeStore(DashboardRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun load_emits_content() = runTest {
        val s = store { HttpStatusCode.OK to dashboardJson }
        val state = s.state.first { it !is HomeUiState.Loading }
        assertIs<HomeUiState.Content>(state)
        assertEquals("120.00", state.data.bills.totalOutstanding)
        assertEquals("Beach Cleanup", state.data.nextActivity?.title)
    }

    @Test fun error_emits_error() = runTest {
        val s = store { HttpStatusCode.InternalServerError to "{}" }
        val state = s.state.first { it !is HomeUiState.Loading }
        assertIs<HomeUiState.Error>(state)
        assertEquals("Couldn't load your summary. Pull to refresh.", state.message)
    }

    @Test fun refresh_toggles_refreshing_and_updates_content() = runTest {
        var outstanding = "120.00"
        val s = store { HttpStatusCode.OK to dashboardJson.replace("120.00", outstanding) }
        s.state.first { it is HomeUiState.Content }
        outstanding = "0.00"
        s.refresh()
        val state = s.state.first { it is HomeUiState.Content && it.data.bills.totalOutstanding == "0.00" }
        assertIs<HomeUiState.Content>(state)
        assertFalse(s.refreshing.value)
    }

    // Derived display

    @Test fun dues_cleared_when_no_outstanding_and_no_pending_proofs() {
        assertTrue(duesCleared(dashboard(outstanding = "0.00", pendingProofs = 0)))
        assertFalse(duesCleared(dashboard(outstanding = "0.00", pendingProofs = 1)))
        assertFalse(duesCleared(dashboard(outstanding = "45.00", pendingProofs = 0)))
    }

    @Test fun dashboard_lunch_status_vote_now_when_open_not_responded() {
        assertEquals(LunchStatus.VoteNow, dashboardLunchStatus(dashLunch(isOpen = true, responded = false)))
    }

    @Test fun dashboard_lunch_status_responded_wins_over_closed() {
        assertEquals(LunchStatus.Responded, dashboardLunchStatus(dashLunch(isOpen = true, responded = true)))
        assertEquals(LunchStatus.Responded, dashboardLunchStatus(dashLunch(isOpen = false, responded = true)))
    }

    @Test fun dashboard_lunch_status_closed_when_not_open_not_responded() {
        assertEquals(LunchStatus.Closed, dashboardLunchStatus(dashLunch(isOpen = false, responded = false)))
    }

    @Test fun dashboard_activity_status_maps_my_status() {
        assertEquals(DashboardActivityStatus.Registered, dashboardActivityStatus(dashActivity("registered")))
        assertEquals(DashboardActivityStatus.Waitlisted, dashboardActivityStatus(dashActivity("waitlisted")))
        assertEquals(DashboardActivityStatus.None, dashboardActivityStatus(dashActivity("none")))
    }

    private fun dashboard(outstanding: String, pendingProofs: Int) = DashboardDto(
        bills = DashboardBillsDto(totalOutstanding = outstanding, availableCredit = "0.00", unpaidCount = 0),
        nextLunch = null, nextActivity = null, openActivitiesCount = 0, pendingProofsCount = pendingProofs,
    )
    private fun dashLunch(isOpen: Boolean, responded: Boolean) =
        DashboardLunchDto(id = 1, date = "2026-07-20", caterer = null, menu = null, isOpen = isOpen, responded = responded)
    private fun dashActivity(myStatus: String) =
        DashboardActivityDto(id = 5, title = "Beach Cleanup", startsAt = null, myStatus = myStatus)
}
