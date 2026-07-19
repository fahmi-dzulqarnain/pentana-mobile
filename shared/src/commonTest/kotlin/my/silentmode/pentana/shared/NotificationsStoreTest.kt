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
import my.silentmode.pentana.shared.presentation.NotifUiState
import my.silentmode.pentana.shared.presentation.NotificationKind
import my.silentmode.pentana.shared.presentation.NotificationsStore
import my.silentmode.pentana.shared.presentation.notificationKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NotificationsStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val pageJson = """
        {"data":[
        {"id":"a1","title":"Lunch voting is open","body":"Vote by Friday","read":false,"created_at":"2026-07-18T09:00:00+08:00"},
        {"id":"a2","title":"Payment received","body":null,"read":true,"created_at":"2026-07-17T12:00:00+08:00"}
        ],"unread_count":1}
    """.trimIndent()

    private fun store(handler: () -> Pair<HttpStatusCode, String>): NotificationsStore {
        val engine = MockEngine { _ ->
            val (status, body) = handler()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return NotificationsStore(NotificationsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun load_emits_content() = runTest {
        val s = store { HttpStatusCode.OK to pageJson }
        val state = s.state.first { it !is NotifUiState.Loading }
        assertIs<NotifUiState.Content>(state)
        assertEquals(2, state.items.size)
        assertEquals("Lunch voting is open", state.items.first().title)
    }

    @Test fun error_emits_error() = runTest {
        val s = store { HttpStatusCode.InternalServerError to "{}" }
        val state = s.state.first { it !is NotifUiState.Loading }
        assertIs<NotifUiState.Error>(state)
        assertEquals("Couldn't load notifications.", state.message)
    }

    // Keyword → kind mapping (union of both platforms' lists, order preserved)

    @Test fun kind_lunch() {
        assertEquals(NotificationKind.Lunch, notificationKind("Lunch voting is open"))
    }

    @Test fun kind_cancelled() {
        assertEquals(NotificationKind.Cancelled, notificationKind("Hike cancelled this weekend"))
    }

    @Test fun kind_payment_covers_proof_payment_and_dues() {
        assertEquals(NotificationKind.Payment, notificationKind("Payment proof approved"))
        assertEquals(NotificationKind.Payment, notificationKind("Payment received"))
        assertEquals(NotificationKind.Payment, notificationKind("July dues issued"))
    }

    @Test fun kind_activity_joined_covers_youre_in_promoted_waitlist() {
        assertEquals(NotificationKind.ActivityJoined, notificationKind("You're in! See you Saturday"))
        assertEquals(NotificationKind.ActivityJoined, notificationKind("Promoted from the waitlist"))
        assertEquals(NotificationKind.ActivityJoined, notificationKind("Waitlist update"))
    }

    @Test fun kind_activity_covers_union_keywords() {
        assertEquals(NotificationKind.Activity, notificationKind("New activity posted"))
        assertEquals(NotificationKind.Activity, notificationKind("A spot opened up"))
        assertEquals(NotificationKind.Activity, notificationKind("Event reminder"))
        assertEquals(NotificationKind.Activity, notificationKind("Hiking trip on Saturday"))
        assertEquals(NotificationKind.Activity, notificationKind("Beach cleanup this weekend"))
        assertEquals(NotificationKind.Activity, notificationKind("Workshop: intro to sourdough"))
    }

    @Test fun kind_general_fallback() {
        assertEquals(NotificationKind.General, notificationKind("Welcome to Pentana"))
    }

    @Test fun kind_cancel_beats_activity_keywords() {
        // "cancel" is checked before "activity"/"hik" — a cancelled activity reads as a cancellation.
        assertEquals(NotificationKind.Cancelled, notificationKind("Activity cancelled: hiking trip"))
    }
}
