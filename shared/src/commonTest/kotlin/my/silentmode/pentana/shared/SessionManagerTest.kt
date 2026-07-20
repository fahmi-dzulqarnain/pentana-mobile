package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.presentation.SessionManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionManagerTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val userJson = """{"data":{"id":7,"name":"Aisyah Rahman","email":"aisyah@org.my","credit":12.5}}"""
    private val loginJson = """{"token":"fresh-token","user":{"id":7,"name":"Aisyah Rahman","email":"aisyah@org.my","credit":12.5}}"""
    private val notificationsJson = """{"data":[],"unread_count":3}"""
    private val markReadJson = """{"unread_count":0}"""

    private class Managed(val manager: SessionManager, val tokenStore: InMemoryTokenStore)

    private fun makeManager(
        startToken: String? = null,
        handler: suspend (String) -> Pair<HttpStatusCode, String>,
    ): Managed {
        val tokenStore = InMemoryTokenStore(startToken)
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = ApiClient("https://x/api/v1", tokenStore, engine)
        return Managed(SessionManager(AuthRepository(client), NotificationsRepository(client)), tokenStore)
    }

    private fun routing(
        meResult: Pair<HttpStatusCode, String>? = null,
        loginResult: Pair<HttpStatusCode, String>? = null,
    ): suspend (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/me") -> meResult ?: (HttpStatusCode.OK to userJson)
            path.endsWith("/login") -> loginResult ?: (HttpStatusCode.OK to loginJson)
            path.endsWith("/logout") -> HttpStatusCode.OK to "{}"
            path.endsWith("/notifications/read") -> HttpStatusCode.OK to markReadJson
            path.endsWith("/notifications") -> HttpStatusCode.OK to notificationsJson
            else -> HttpStatusCode.NotFound to "{}"
        }
    }

    @Test fun bootstrap_with_valid_token_loads_user_and_badge() = runTest {
        val managed = makeManager(startToken = "stored-token", handler = routing())
        managed.manager.bootstrap()
        val sessionState = managed.manager.state.value
        assertEquals("Aisyah Rahman", sessionState.user?.name)
        assertEquals(3, sessionState.unread)
        assertFalse(sessionState.bootstrapping)
    }

    @Test fun bootstrap_with_stale_token_logs_out() = runTest {
        val managed = makeManager(startToken = "stale-token", handler = routing(meResult = HttpStatusCode.Unauthorized to "{}"))
        managed.manager.bootstrap()
        val sessionState = managed.manager.state.value
        assertNull(sessionState.user)
        assertFalse(sessionState.bootstrapping)
        assertNull(managed.tokenStore.get()) // logout cleared the stale token
    }

    @Test fun bootstrap_without_token_just_finishes() = runTest {
        val managed = makeManager(startToken = null, handler = routing())
        managed.manager.bootstrap()
        assertNull(managed.manager.state.value.user)
        assertFalse(managed.manager.state.value.bootstrapping)
    }

    @Test fun login_success_sets_user_refreshes_badge_and_returns_user() = runTest {
        // Pins AuthRepository/SessionManager trimming the email before it hits the wire: the mock
        // fails the request outright if the raw (untrimmed) email survives into the request body,
        // so this test genuinely fails if `.trim()` is ever removed from SessionManager.login.
        val tokenStore = InMemoryTokenStore(null)
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val requestBodyText = when (val requestBody = request.body) {
                is TextContent -> requestBody.text
                is OutgoingContent.ByteArrayContent -> requestBody.bytes().decodeToString()
                else -> ""
            }
            val (status, responseBody) = if (path.endsWith("/login") && requestBodyText.contains(" aisyah")) {
                HttpStatusCode.UnprocessableEntity to "{}"
            } else {
                routing()(path)
            }
            respond(responseBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = ApiClient("https://x/api/v1", tokenStore, engine)
        val manager = SessionManager(AuthRepository(client), NotificationsRepository(client))

        val loggedIn = manager.login(email = " aisyah@org.my ", password = "secret", deviceName = "Test")
        assertNotNull(loggedIn)
        assertEquals("Aisyah Rahman", manager.state.value.user?.name)
        assertEquals(3, manager.state.value.unread)
        assertNull(manager.loginError.value)
        assertEquals("fresh-token", tokenStore.get())
    }

    @Test fun login_failure_sets_error_and_returns_null() = runTest {
        val managed = makeManager(startToken = null, handler = routing(loginResult = HttpStatusCode.UnprocessableEntity to "{}"))
        val loggedIn = managed.manager.login(email = "aisyah@org.my", password = "wrong", deviceName = "Test")
        assertNull(loggedIn)
        assertNull(managed.manager.state.value.user)
        assertEquals("The provided credentials are incorrect.", managed.manager.loginError.value)
        managed.manager.dismissLoginError()
        assertNull(managed.manager.loginError.value)
    }

    @Test fun next_login_attempt_clears_previous_error() = runTest {
        var failNext = true
        val managed = makeManager(startToken = null) { path ->
            when {
                path.endsWith("/login") -> {
                    if (failNext) { failNext = false; HttpStatusCode.UnprocessableEntity to "{}" } else HttpStatusCode.OK to loginJson
                }
                else -> routing()(path)
            }
        }
        managed.manager.login(email = "aisyah@org.my", password = "wrong", deviceName = "Test")
        assertNotNull(managed.manager.loginError.value)
        managed.manager.login(email = "aisyah@org.my", password = "right", deviceName = "Test")
        assertNull(managed.manager.loginError.value)
    }

    @Test fun logout_resets_state_even_if_server_call_fails() = runTest {
        val managed = makeManager(startToken = "stored-token") { path ->
            when {
                path.endsWith("/logout") -> HttpStatusCode.InternalServerError to "{}"
                else -> routing()(path)
            }
        }
        managed.manager.bootstrap()
        assertNotNull(managed.manager.state.value.user)
        managed.manager.logout()
        assertNull(managed.manager.state.value.user)
        assertEquals(0, managed.manager.state.value.unread)
        assertFalse(managed.manager.state.value.bootstrapping)
    }

    @Test fun badge_and_mark_read_are_noops_when_logged_out() = runTest {
        var notificationRequests = 0
        val managed = makeManager(startToken = null) { path ->
            if (path.contains("/notifications")) notificationRequests += 1
            routing()(path)
        }
        managed.manager.refreshBadge()
        managed.manager.markAllRead()
        assertEquals(0, notificationRequests)
    }

    @Test fun mark_all_read_zeroes_unread() = runTest {
        val managed = makeManager(startToken = "stored-token", handler = routing())
        managed.manager.bootstrap()
        assertEquals(3, managed.manager.state.value.unread)
        managed.manager.markAllRead()
        assertEquals(0, managed.manager.state.value.unread)
    }

    @Test fun on_logged_in_sets_user_and_refreshes_badge() = runTest {
        val managed = makeManager(startToken = "ceremony-token", handler = routing())
        val ceremonyUser = my.silentmode.pentana.shared.model.UserDto(id = 7, name = "Aisyah Rahman", email = "aisyah@org.my")
        managed.manager.onLoggedIn(ceremonyUser)
        managed.manager.state.first { it.unread == 3 }
        assertEquals("Aisyah Rahman", managed.manager.state.value.user?.name)
    }

    @Test fun stale_badge_fetch_after_logout_is_discarded() = runTest {
        // Pins the session-epoch guard: a badge fetch started BEFORE logout must not land its
        // (now-stale) unread count into the session that begins fresh right after logout.
        val notificationsGate = CompletableDeferred<Unit>()
        // MockEngine may service the stale request on a different dispatcher than this test
        // coroutine, so we cannot infer "the fetch is now parked" from scheduler ordering alone —
        // this deferred is a real cross-thread signal fired right as the stale call parks, so the
        // test can deterministically wait for "epoch captured, now blocked on notificationsGate"
        // before calling logout(), instead of guessing at interleaving.
        val staleFetchParked = CompletableDeferred<Unit>()
        var notificationRequestCount = 0
        val managed = makeManager(startToken = "stored-token") { path ->
            when {
                path.endsWith("/notifications") -> {
                    notificationRequestCount += 1
                    if (notificationRequestCount > 1) {
                        staleFetchParked.complete(Unit)
                        notificationsGate.await() // second call: stale, parked
                    }
                    HttpStatusCode.OK to notificationsJson
                }
                else -> routing()(path)
            }
        }
        managed.manager.bootstrap() // first /notifications call completes inline; unread == 3
        assertEquals(3, managed.manager.state.value.unread)

        val staleRefresh = launch(Dispatchers.Main) { managed.manager.refreshBadge() }
        staleFetchParked.await() // the stale fetch has captured its epoch and is now blocked

        managed.manager.logout()
        assertNull(managed.manager.state.value.user)
        assertEquals(0, managed.manager.state.value.unread)

        notificationsGate.complete(Unit) // the stale fetch now resolves...
        staleRefresh.join()
        assertEquals(0, managed.manager.state.value.unread) // ...but must not overwrite the badge
        assertNull(managed.manager.state.value.user)
    }

    @Test fun stale_mark_all_read_across_logout_login_is_discarded() = runTest {
        // Pins the same epoch rule on markAllRead: a mark-read parked across logout→login must
        // not zero the NEW session's badge (which login's refresh just set).
        val markReadGate = CompletableDeferred<Unit>()
        val staleMarkParked = CompletableDeferred<Unit>() // real cross-thread signal, as above
        val managed = makeManager(startToken = "stored-token") { path ->
            when {
                path.endsWith("/notifications/read") -> {
                    staleMarkParked.complete(Unit)
                    markReadGate.await()
                    HttpStatusCode.OK to markReadJson
                }
                else -> routing()(path)
            }
        }
        managed.manager.bootstrap()
        assertEquals(3, managed.manager.state.value.unread)

        val staleMark = launch(Dispatchers.Main) { managed.manager.markAllRead() }
        staleMarkParked.await() // epoch captured, request parked

        managed.manager.logout()
        managed.manager.login(email = "aisyah@org.my", password = "right", deviceName = "Test")
        assertEquals(3, managed.manager.state.value.unread) // fresh session's badge

        markReadGate.complete(Unit) // the abandoned mark-read now resolves...
        staleMark.join()
        assertEquals(3, managed.manager.state.value.unread) // ...but must not zero the new badge
    }

    @Test fun mark_all_read_zeroes_badge_even_when_server_fails() = runTest {
        val managed = makeManager(startToken = "stored-token") { path ->
            when {
                path.endsWith("/notifications/read") -> HttpStatusCode.InternalServerError to "{}"
                else -> routing()(path)
            }
        }
        managed.manager.bootstrap()
        assertEquals(3, managed.manager.state.value.unread)
        managed.manager.markAllRead()
        assertEquals(0, managed.manager.state.value.unread)
    }
}
