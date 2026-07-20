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
import my.silentmode.pentana.shared.presentation.ActivitiesStore
import my.silentmode.pentana.shared.presentation.ActivitiesUiState
import my.silentmode.pentana.shared.presentation.RegState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ActivitiesStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun activityJson(id: Long, myStatus: String) = """
        {"id":$id,"title":"Beach Cleanup","description":"<p>Bring water</p>","starts_at":"2026-07-25T08:00:00+08:00",
        "location":"Port Dickson","spots_left":4,"is_open":true,"my_status":"$myStatus","waitlist_position":null,
        "questions":[{"key":"name","label":"Name","type":"text","required":true}]}
    """.trimIndent()

    private val listJson = """{"data":[${activityJson(1, "none")},${activityJson(2, "none")}]}"""
    private val registeredJson = """{"data":${activityJson(1, "registered")}}"""
    private val cancelledJson = """{"data":${activityJson(1, "none")}}"""

    private fun makeStore(handler: (String) -> Pair<HttpStatusCode, String>): ActivitiesStore {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    private fun routing(
        registerResult: Pair<HttpStatusCode, String>? = null,
        cancelResult: Pair<HttpStatusCode, String>? = null,
    ): (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/register") -> registerResult ?: (HttpStatusCode.OK to registeredJson)
            path.endsWith("/cancel") -> cancelResult ?: (HttpStatusCode.OK to cancelledJson)
            path.endsWith("/activities") -> HttpStatusCode.OK to listJson
            else -> HttpStatusCode.NotFound to "{}"
        }
    }

    @Test fun load_emits_content() = runTest {
        val store = makeStore(routing())
        val uiState = store.state.first { it !is ActivitiesUiState.Loading }
        assertIs<ActivitiesUiState.Content>(uiState)
        assertEquals(2, uiState.activities.size)
    }

    @Test fun error_emits_error() = runTest {
        val store = makeStore { _ -> HttpStatusCode.InternalServerError to "{}" }
        val uiState = store.state.first { it !is ActivitiesUiState.Loading }
        assertIs<ActivitiesUiState.Error>(uiState)
        assertEquals("Something went wrong. Pull down to refresh.", uiState.message)
    }

    @Test fun register_success_replaces_activity_and_reaches_success() = runTest {
        val store = makeStore(routing())
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.reg.first { it is RegState.Success }
        val uiState = store.state.first { it is ActivitiesUiState.Content }
        assertIs<ActivitiesUiState.Content>(uiState)
        assertEquals("registered", uiState.activities.first { it.id == 1L }.myStatus)
        assertEquals("none", uiState.activities.first { it.id == 2L }.myStatus) // untouched
    }

    @Test fun register_failure_sets_reg_error() = runTest {
        val store = makeStore(routing(registerResult = HttpStatusCode.UnprocessableEntity to "{}"))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        val regState = store.reg.first { it is RegState.Error }
        assertIs<RegState.Error>(regState)
        assertEquals("Registration failed. Please check your answers.", regState.message)
    }

    @Test fun register_marks_in_flight_for_card_dimming_and_guards_duplicates() = runTest {
        val gate = CompletableDeferred<Unit>()
        var registerRequests = 0
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/register") -> { registerRequests += 1; gate.await(); registeredJson }
                path.endsWith("/activities") -> listJson
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.inFlight.first { 1L in it } // request pending — card dims
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah")) // guarded duplicate
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }
        assertEquals(1, registerRequests)
    }

    @Test fun in_flight_guard_alone_blocks_retap_after_reset() = runTest {
        // Isolates the inFlight guard: resetReg() disarms the Submitting guard (reg is Idle again),
        // so a retap of the SAME id is stopped by the inFlight membership check alone.
        val gate = CompletableDeferred<Unit>()
        var registerRequests = 0
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/register") -> { registerRequests += 1; gate.await(); registeredJson }
                path.endsWith("/activities") -> listJson
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.reg.first { it is RegState.Submitting }
        store.resetReg() // abandons the sheet — Submitting guard disarmed, request still in flight
        assertIs<RegState.Idle>(store.reg.value)
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah")) // must hit the inFlight guard
        assertEquals(1, registerRequests)
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }
        assertEquals(1, registerRequests)
    }

    @Test fun submitting_guard_serializes_across_ids() = runTest {
        // Isolates the Submitting guard: a DIFFERENT activity id is not in inFlight, so only the
        // reg-machine check can stop the second registration while the first is in flight.
        val gate = CompletableDeferred<Unit>()
        var registerRequests = 0
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/register") -> { registerRequests += 1; gate.await(); registeredJson }
                path.endsWith("/activities") -> listJson
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.reg.first { it is RegState.Submitting }
        store.register(activityId = 2, answers = mapOf("name" to "Aisyah")) // different id — only the Submitting guard applies
        assertEquals(1, registerRequests)
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }
        assertEquals(1, registerRequests)
    }

    @Test fun reset_discards_late_completion_of_abandoned_registration() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/register") -> { gate.await(); registeredJson }
                path.endsWith("/activities") -> listJson
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.reg.first { it is RegState.Submitting }
        store.resetReg() // sheet dismissed mid-flight
        assertIs<RegState.Idle>(store.reg.value)
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }
        // The registration DID happen server-side: the optimistic replace still lands…
        assertEquals("registered", (store.state.value as ActivitiesUiState.Content).activities.first { it.id == 1L }.myStatus)
        // …but the stale Success must not replay into the reg flow.
        assertIs<RegState.Idle>(store.reg.value)
    }

    @Test fun cancel_failure_sets_action_error_and_cancel_success_replaces() = runTest {
        var failCancel = true
        val store = makeStore { path ->
            when {
                path.endsWith("/cancel") -> {
                    if (failCancel) { failCancel = false; HttpStatusCode.InternalServerError to "{}" } else HttpStatusCode.OK to cancelledJson
                }
                path.endsWith("/activities") -> HttpStatusCode.OK to listJson
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        store.state.first { it is ActivitiesUiState.Content }
        store.cancel(activityId = 1)
        store.inFlight.first { it.isEmpty() }
        assertEquals("Couldn't cancel. Please try again.", store.actionError.value)
        store.cancel(activityId = 1) // retry clears the error as it starts
        store.inFlight.first { it.isEmpty() }
        assertNull(store.actionError.value)
    }

    @Test fun refresh_failure_replaces_content_with_error() = runTest {
        var fail = false
        val store = makeStore { path ->
            if (fail) HttpStatusCode.InternalServerError to "{}" else routing()(path)
        }
        store.state.first { it is ActivitiesUiState.Content }
        fail = true
        store.refresh()
        assertIs<ActivitiesUiState.Error>(store.state.value)
        assertFalse(store.refreshing.value)
    }
}
