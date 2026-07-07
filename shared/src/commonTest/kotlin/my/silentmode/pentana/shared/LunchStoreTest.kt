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
import my.silentmode.pentana.shared.model.LunchDto
import my.silentmode.pentana.shared.model.LunchOptionDto
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.LunchStore
import my.silentmode.pentana.shared.presentation.LunchUiState
import my.silentmode.pentana.shared.presentation.lunchClosedSummary
import my.silentmode.pentana.shared.presentation.lunchStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LunchStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val openLunchJson = """
        {"data":[{"id":1,"date":"2026-07-13","caterer":"Dapur Selera","menu":"Nasi Lemak Royale",
        "deadline":"2026-07-12T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":10,"name":"Chicken"},{"meal_option_id":11,"name":"Beef"}],
        "responded":false,"my_meal_option_id":null}]}
    """.trimIndent()

    private val chosenLunchJson = """
        {"data":{"id":1,"date":"2026-07-13","caterer":"Dapur Selera","menu":"Nasi Lemak Royale",
        "deadline":"2026-07-12T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":10,"name":"Chicken"},{"meal_option_id":11,"name":"Beef"}],
        "responded":true,"my_meal_option_id":10}}
    """.trimIndent()

    private val notAttendingLunchJson = """
        {"data":{"id":1,"date":"2026-07-13","caterer":"Dapur Selera","menu":"Nasi Lemak Royale",
        "deadline":"2026-07-12T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":10,"name":"Chicken"},{"meal_option_id":11,"name":"Beef"}],
        "responded":true,"my_meal_option_id":null}}
    """.trimIndent()

    // Two lunches so replace()'s per-id targeting is actually exercised.
    private val twoLunchesJson = """
        {"data":[
        {"id":1,"date":"2026-07-13","caterer":"Dapur Selera","menu":"Nasi Lemak Royale",
        "deadline":"2026-07-12T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":10,"name":"Chicken"},{"meal_option_id":11,"name":"Beef"}],
        "responded":false,"my_meal_option_id":null},
        {"id":2,"date":"2026-07-20","caterer":"The Daily Grind","menu":"Chicken Chop",
        "deadline":"2026-07-19T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":20,"name":"Fish"},{"meal_option_id":21,"name":"Pasta"}],
        "responded":false,"my_meal_option_id":null}
        ]}
    """.trimIndent()

    private fun store(handler: (String) -> Pair<HttpStatusCode, String>): LunchStore {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LunchStore(LunchRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun load_emits_content() = runTest {
        val s = store { HttpStatusCode.OK to openLunchJson }
        // The store loads on its own scope; await the async settle rather than reading .value early.
        val state = s.state.first { it !is LunchUiState.Loading }
        assertIs<LunchUiState.Content>(state)
        assertEquals(1, state.lunches.size)
        assertEquals("Nasi Lemak Royale", state.lunches.first().menu)
    }

    @Test fun choose_updates_only_the_target_lunch() = runTest {
        val s = store { path -> if (path.endsWith("/respond")) HttpStatusCode.OK to chosenLunchJson else HttpStatusCode.OK to twoLunchesJson }
        s.state.first { it is LunchUiState.Content && it.lunches.size == 2 } // let the initial load settle before choosing
        s.choose(lunchId = 1, mealOptionId = 10)
        val state = s.state.first { it is LunchUiState.Content && it.lunches.first { l -> l.id == 1L }.responded }
        assertIs<LunchUiState.Content>(state)
        val chosen = state.lunches.first { it.id == 1L }
        assertTrue(chosen.responded)
        assertEquals(10L, chosen.myMealOptionId)
        // the other lunch is left untouched
        val other = state.lunches.first { it.id == 2L }
        assertFalse(other.responded)
        assertNull(other.myMealOptionId)
    }

    @Test fun not_attending_updates_the_lunch() = runTest {
        val s = store { path -> if (path.endsWith("/respond")) HttpStatusCode.OK to notAttendingLunchJson else HttpStatusCode.OK to openLunchJson }
        s.state.first { it is LunchUiState.Content } // let the initial load settle before responding
        s.notAttending(lunchId = 1)
        val state = s.state.first { it is LunchUiState.Content && it.lunches.first().responded }
        assertIs<LunchUiState.Content>(state)
        assertTrue(state.lunches.first().responded)
        assertNull(state.lunches.first().myMealOptionId)
    }

    @Test fun choose_marks_lunch_in_flight_until_it_completes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            val body = if (request.url.fullPath.endsWith("/respond")) {
                gate.await() // hold the response so the in-flight window is observable
                chosenLunchJson
            } else {
                openLunchJson
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val s = LunchStore(LunchRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        s.state.first { it is LunchUiState.Content } // let the initial load settle
        assertTrue(s.inFlight.value.isEmpty())
        s.choose(lunchId = 1, mealOptionId = 10)
        s.inFlight.first { it.contains(1L) } // the request is pending
        s.choose(lunchId = 1, mealOptionId = 11) // guarded: ignored while one is in flight
        gate.complete(Unit)
        s.inFlight.first { it.isEmpty() } // cleared after completion
        // the guarded second tap never ran, so the first option stands
        assertEquals(10L, (s.state.value as LunchUiState.Content).lunches.first().myMealOptionId)
    }

    @Test fun error_emits_error() = runTest {
        val s = store { HttpStatusCode.InternalServerError to "{}" }
        assertIs<LunchUiState.Error>(s.state.first { it !is LunchUiState.Loading })
    }

    @Test fun status_is_vote_now_when_open_and_not_responded() {
        assertEquals(LunchStatus.VoteNow, lunchStatus(lunch(isOpen = true, responded = false)))
    }

    @Test fun status_is_responded_when_open_and_responded() {
        assertEquals(LunchStatus.Responded, lunchStatus(lunch(isOpen = true, responded = true)))
    }

    @Test fun status_is_closed_when_not_open() {
        assertEquals(LunchStatus.Closed, lunchStatus(lunch(isOpen = false, responded = false)))
    }

    @Test fun closed_summary_names_the_ordered_option() {
        assertEquals("Ordering closed — you ordered Beef.", lunchClosedSummary(lunch(isOpen = false, responded = true, myMealOptionId = 11)))
    }

    @Test fun closed_summary_reports_no_order_when_not_responded() {
        assertEquals("Ordering closed — no order placed.", lunchClosedSummary(lunch(isOpen = false, responded = false)))
    }

    @Test fun closed_summary_reports_not_attending_when_no_option() {
        assertEquals("Ordering closed — you marked not attending.", lunchClosedSummary(lunch(isOpen = false, responded = true, myMealOptionId = null)))
    }

    private fun lunch(isOpen: Boolean, responded: Boolean, myMealOptionId: Long? = null) = LunchDto(
        id = 1, date = "2026-07-13", caterer = "Dapur Selera", menu = "Nasi Lemak Royale",
        deadline = null, isOpen = isOpen,
        options = listOf(LunchOptionDto(10, "Chicken"), LunchOptionDto(11, "Beef")),
        responded = responded, myMealOptionId = myMealOptionId,
    )
}
