package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.SupportTicketDetailDto
import my.silentmode.pentana.shared.presentation.SupportCreateState
import my.silentmode.pentana.shared.presentation.SupportImage
import my.silentmode.pentana.shared.presentation.SupportListStore
import my.silentmode.pentana.shared.presentation.SupportListUiState
import my.silentmode.pentana.shared.presentation.SupportStatus
import my.silentmode.pentana.shared.presentation.SupportThreadStore
import my.silentmode.pentana.shared.presentation.SupportThreadUiState
import my.silentmode.pentana.shared.presentation.closedNote
import my.silentmode.pentana.shared.presentation.supportCardSubtitle
import my.silentmode.pentana.shared.presentation.supportHeaderLabel
import my.silentmode.pentana.shared.presentation.supportListLabel
import my.silentmode.pentana.shared.presentation.supportStatus
import my.silentmode.pentana.shared.presentation.supportTypeLabel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun ticketJson(id: Long, status: String, at: String) =
        """{"id":$id,"type":"question","subject":"T$id","status":"$status","last_message_at":"$at","last_message_from_committee":false}"""

    private fun detailJson(status: String = "needs_reply", messages: String = MESSAGE_9, closedByCommittee: Boolean = false) =
        """{"data":{"id":7,"type":"bug","subject":"Blank page","status":"$status","last_message_at":"2026-10-26T10:00:00+08:00",
           "closed_at":${if (status == "closed") "\"2026-10-26T12:00:00+08:00\"" else "null"},
           "closed_by_committee":$closedByCommittee,"messages":[$messages]}}"""

    private fun repo(handler: (method: HttpMethod, path: String) -> Pair<HttpStatusCode, String>): SupportRepository {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.method, request.url.encodedPath)
            val type = if (body.startsWith("PNG")) "image/png" else "application/json"
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }
        return SupportRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine))
    }

    // ── Display helpers ──────────────────────────────────────────────────────────────────────

    @Test fun status_maps_the_server_values() {
        assertEquals(SupportStatus.OPEN, supportStatus("needs_reply"))
        assertEquals(SupportStatus.REPLIED, supportStatus("waiting_on_member"))
        assertEquals(SupportStatus.CLOSED, supportStatus("closed"))
        assertEquals(SupportStatus.OPEN, supportStatus("something_new"))
    }

    @Test fun labels_word_each_status() {
        assertEquals("Replied", supportListLabel(SupportStatus.REPLIED))
        assertEquals("Committee replied", supportHeaderLabel(SupportStatus.REPLIED))
        assertEquals("Open", supportListLabel(SupportStatus.OPEN))
        assertEquals("Closed", supportHeaderLabel(SupportStatus.CLOSED))
        assertEquals("Bug", supportTypeLabel("bug"))
        assertEquals("Suggestion", supportTypeLabel("suggestion"))
        assertEquals("Question", supportTypeLabel("question"))
    }

    @Test fun card_subtitle_counts_waiting_replies() {
        assertEquals("Questions, bugs or ideas", supportCardSubtitle(0))
        assertEquals("Questions, bugs or ideas · 1 reply", supportCardSubtitle(1))
        assertEquals("Questions, bugs or ideas · 3 replies", supportCardSubtitle(3))
    }

    @Test fun closed_note_says_who_closed_it() {
        fun detail(status: String, byCommittee: Boolean) = SupportTicketDetailDto(
            id = 1, type = "question", subject = "S", status = status, lastMessageAt = "x", closedByCommittee = byCommittee,
        )
        assertNull(closedNote(detail("needs_reply", false)))
        assertEquals("Closed by the committee", closedNote(detail("closed", true)))
        assertEquals("You marked this resolved", closedNote(detail("closed", false)))
    }

    // ── SupportListStore ─────────────────────────────────────────────────────────────────────

    @Test fun list_splits_open_and_closed_with_replies_first() = runTest {
        val store = SupportListStore(repo { _, _ ->
            HttpStatusCode.OK to """{"data":[${ticketJson(1, "needs_reply", "2026-10-26T12:00:00+08:00")},
                ${ticketJson(2, "waiting_on_member", "2026-10-25T09:00:00+08:00")},
                ${ticketJson(3, "closed", "2026-10-24T09:00:00+08:00")}]}"""
        })

        val content = assertIs<SupportListUiState.Content>(store.state.first { it !is SupportListUiState.Loading })
        assertEquals(listOf(2L, 1L), content.open.map { it.id })
        assertEquals(listOf(3L), content.closed.map { it.id })
    }

    @Test fun an_empty_list_is_content_not_an_error() = runTest {
        val store = SupportListStore(repo { _, _ -> HttpStatusCode.OK to """{"data":[]}""" })

        val content = assertIs<SupportListUiState.Content>(store.state.first { it !is SupportListUiState.Loading })
        assertTrue(content.open.isEmpty() && content.closed.isEmpty())
    }

    @Test fun a_failed_list_is_an_error_state() = runTest {
        val store = SupportListStore(repo { _, _ -> HttpStatusCode.InternalServerError to "{}" })

        assertIs<SupportListUiState.Error>(store.state.first { it !is SupportListUiState.Loading })
    }

    @Test fun create_sends_then_reloads_the_list() = runTest {
        var created = false
        val store = SupportListStore(repo { method, _ ->
            if (method == HttpMethod.Post) {
                created = true
                HttpStatusCode.Created to detailJson()
            } else {
                HttpStatusCode.OK to if (created) """{"data":[${ticketJson(7, "needs_reply", "2026-10-26T10:00:00+08:00")}]}""" else """{"data":[]}"""
            }
        })
        store.state.first { it is SupportListUiState.Content }

        store.create("bug", "Blank page", "It is blank", null, null)

        assertEquals(SupportCreateState.Sent(7), store.create.first { it is SupportCreateState.Sent })
        val content = store.state.first { (it as? SupportListUiState.Content)?.open?.isNotEmpty() == true }
        assertEquals(listOf(7L), (content as SupportListUiState.Content).open.map { it.id })
    }

    @Test fun create_with_blank_subject_or_body_does_nothing() = runTest {
        var posts = 0
        val store = SupportListStore(repo { method, _ ->
            if (method == HttpMethod.Post) posts++
            HttpStatusCode.OK to """{"data":[]}"""
        })

        store.create("question", "  ", "Body", null, null)
        store.create("question", "Subject", "", null, null)

        assertEquals(0, posts)
        assertEquals(SupportCreateState.Idle, store.create.value)
    }

    @Test fun create_error_surfaces_and_can_be_reset() = runTest {
        val store = SupportListStore(repo { method, _ ->
            if (method == HttpMethod.Post) HttpStatusCode.UnprocessableEntity to """{"message":"Too big"}"""
            else HttpStatusCode.OK to """{"data":[]}"""
        })

        store.create("bug", "Blank", "Body", byteArrayOf(1), "s.jpg")

        assertIs<SupportCreateState.Error>(store.create.first { it is SupportCreateState.Error })
        store.resetCreate()
        assertEquals(SupportCreateState.Idle, store.create.value)
    }

    // ── SupportThreadStore ───────────────────────────────────────────────────────────────────

    @Test fun thread_loads_the_conversation() = runTest {
        val store = SupportThreadStore(repo { _, _ -> HttpStatusCode.OK to detailJson() }, 7)

        val content = assertIs<SupportThreadUiState.Content>(store.state.first { it !is SupportThreadUiState.Loading })
        assertEquals("Blank page", content.detail.subject)
        assertEquals(1, content.detail.messages.size)
    }

    @Test fun a_missing_ticket_is_an_error_state_not_a_crash() = runTest {
        val store = SupportThreadStore(repo { _, _ -> HttpStatusCode.NotFound to """{"message":"Not Found"}""" }, 7)

        assertIs<SupportThreadUiState.Error>(store.state.first { it !is SupportThreadUiState.Loading })
    }

    @Test fun reply_appends_and_refetches_the_status() = runTest {
        var replied = false
        val store = SupportThreadStore(repo { method, path ->
            when {
                method == HttpMethod.Post && path.endsWith("/replies") -> {
                    replied = true
                    HttpStatusCode.Created to """{"data":$MESSAGE_10}"""
                }
                replied -> HttpStatusCode.OK to detailJson(messages = "$MESSAGE_9,$MESSAGE_10")
                else -> HttpStatusCode.OK to detailJson(status = "closed")
            }
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.reply("One more thing")

        store.inFlight.first { it.isEmpty() }
        val content = assertIs<SupportThreadUiState.Content>(store.state.value)
        assertEquals(listOf(9L, 10L), content.detail.messages.map { it.id })
        assertEquals("needs_reply", content.detail.status)
        assertTrue(store.inFlight.value.isEmpty())
    }

    @Test fun a_double_reply_posts_once() = runTest {
        var posts = 0
        val gate = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                posts++
                gate.await()
                respond("""{"data":$MESSAGE_10}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(detailJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val store = SupportThreadStore(SupportRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)), 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.reply("Hello")
        store.reply("Hello")
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }

        assertEquals(1, posts)
    }

    @Test fun a_blank_reply_is_ignored() = runTest {
        var posts = 0
        val store = SupportThreadStore(repo { method, _ ->
            if (method == HttpMethod.Post) posts++
            HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.reply("   ")

        assertEquals(0, posts)
    }

    @Test fun a_failed_reply_surfaces_an_error_and_keeps_the_thread() = runTest {
        val store = SupportThreadStore(repo { method, _ ->
            if (method == HttpMethod.Post) HttpStatusCode.InternalServerError to "{}" else HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.reply("Hello")

        assertEquals("Couldn't send your reply. Please try again.", store.actionError.first { it != null })
        assertIs<SupportThreadUiState.Content>(store.state.value)
        store.dismissActionError()
        assertNull(store.actionError.value)
    }

    @Test fun a_failed_reply_hands_the_text_back_for_the_reply_box() = runTest {
        val store = SupportThreadStore(repo { method, _ ->
            if (method == HttpMethod.Post) HttpStatusCode.TooManyRequests to "{}" else HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.reply("  My long explanation  ")

        assertEquals("My long explanation", store.failedReply.first { it != null })
        store.consumeFailedReply()
        assertNull(store.failedReply.value)
    }

    @Test fun a_failed_refresh_keeps_the_conversation_and_reports_it() = runTest {
        var failNow = false
        val store = SupportThreadStore(repo { _, _ ->
            if (failNow) HttpStatusCode.InternalServerError to "{}" else HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }
        failNow = true

        store.refresh()

        assertIs<SupportThreadUiState.Content>(store.state.value)
        assertEquals("Couldn't refresh. Please try again.", store.actionError.value)
    }

    @Test fun reset_while_sending_does_not_disarm_the_double_submit_guard() = runTest {
        var posts = 0
        val gate = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                posts++
                gate.await()
                respond(detailJson(), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"data":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val store = SupportListStore(SupportRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is SupportListUiState.Content }

        store.create("bug", "Blank", "Body", null, null)
        store.resetCreate()
        store.create("bug", "Blank", "Body", null, null)
        gate.complete(Unit)
        store.create.first { it is SupportCreateState.Sent }

        assertEquals(1, posts)
    }

    @Test fun resolve_closes_the_ticket() = runTest {
        val store = SupportThreadStore(repo { method, path ->
            if (method == HttpMethod.Post && path.endsWith("/resolve")) HttpStatusCode.OK to detailJson(status = "closed")
            else HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.resolve()

        val content = assertIs<SupportThreadUiState.Content>(store.state.first { (it as? SupportThreadUiState.Content)?.detail?.status == "closed" })
        assertEquals("You marked this resolved", closedNote(content.detail))
    }

    @Test fun a_failed_image_is_failed_not_a_crash() = runTest {
        val store = SupportThreadStore(repo { _, path ->
            if (path.endsWith("/attachment")) HttpStatusCode.NotFound to """{"message":"Not Found"}""" else HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.loadImage(9, ATTACHMENT_URL)

        val failed = store.images.first { entries -> entries.any { it.messageId == 9L && it.image !is SupportImage.Loading } }
        assertEquals(SupportImage.Failed, failed.single { it.messageId == 9L }.image)
    }

    @Test fun images_load_once_per_message() = runTest {
        var fetches = 0
        val store = SupportThreadStore(repo { _, path ->
            if (path.endsWith("/attachment")) {
                fetches++
                HttpStatusCode.OK to "PNG-bytes"
            } else HttpStatusCode.OK to detailJson()
        }, 7)
        store.state.first { it is SupportThreadUiState.Content }

        store.loadImage(9, ATTACHMENT_URL)
        store.loadImage(9, ATTACHMENT_URL)

        val entries = store.images.first { list -> list.any { it.messageId == 9L && it.image is SupportImage.Loaded } }
        assertEquals(1, entries.size)
        val loaded = assertIs<SupportImage.Loaded>(entries.single().image)
        assertContentEquals("PNG-bytes".encodeToByteArray(), loaded.bytes)
        assertEquals(1, fetches)
    }

    private companion object {
        const val ATTACHMENT_URL = "https://x/api/v1/support-tickets/messages/9/attachment"
        const val MESSAGE_9 = """{"id":9,"body":"It is blank","from_committee":false,"is_mine":true,"created_at":"2026-10-26T10:00:00+08:00","attachment_url":"$ATTACHMENT_URL"}"""
        const val MESSAGE_10 = """{"id":10,"body":"One more thing","from_committee":false,"is_mine":true,"created_at":"2026-10-26T11:00:00+08:00","attachment_url":null}"""
    }
}
