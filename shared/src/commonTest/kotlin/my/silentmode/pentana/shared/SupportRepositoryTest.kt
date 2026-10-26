package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.NotificationDto
import my.silentmode.pentana.shared.model.UserDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportRepositoryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val json = Json { ignoreUnknownKeys = true }

    private val detailJson = """
        {"data":{"id":7,"type":"bug","subject":"Blank page","status":"needs_reply",
          "last_message_at":"2026-10-26T10:00:00+08:00","last_message_from_committee":false,
          "closed_at":null,"closed_by_committee":false,
          "messages":[{"id":9,"body":"It is blank","from_committee":false,"is_mine":true,
            "created_at":"2026-10-26T10:00:00+08:00","attachment_url":"https://x/api/v1/support-tickets/messages/9/attachment"}]}}
    """.trimIndent()

    private fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: ByteArray = detailJson.encodeToByteArray(),
        contentType: String = "application/json",
        onRequest: (HttpRequestData) -> Unit = {},
    ): SupportRepository {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
        }
        return SupportRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine))
    }

    /** The multipart body as text, so tests can look for its form parts. */
    private suspend fun bodyText(request: HttpRequestData): String = when (val content = request.body) {
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        is OutgoingContent.WriteChannelContent -> {
            val channel = ByteChannel()
            content.writeTo(channel)
            channel.flushAndClose()
            channel.readRemaining().readByteArray().decodeToString()
        }
        else -> ""
    }

    @Test fun tickets_decodes_the_list() = runTest {
        var seen: HttpRequestData? = null
        val repo = repository(
            body = """{"data":[{"id":1,"type":"question","subject":"Paid twice?","status":"waiting_on_member",
                "last_message_at":"2026-10-26T10:00:00+08:00","last_message_from_committee":true}]}""".encodeToByteArray(),
            onRequest = { seen = it },
        )

        val tickets = repo.tickets()

        assertEquals("/api/v1/support-tickets", seen?.url?.encodedPath)
        assertEquals("Bearer t", seen?.headers?.get(HttpHeaders.Authorization))
        val ticket = tickets.single()
        assertEquals(1L, ticket.id)
        assertEquals("question", ticket.type)
        assertEquals("Paid twice?", ticket.subject)
        assertEquals("waiting_on_member", ticket.status)
        assertEquals("2026-10-26T10:00:00+08:00", ticket.lastMessageAt)
        assertTrue(ticket.lastMessageFromCommittee)
    }

    @Test fun ticket_decodes_the_thread() = runTest {
        val detail = repository().ticket(7)

        assertEquals("bug", detail.type)
        assertNull(detail.closedAt)
        assertFalse(detail.closedByCommittee)
        val message = detail.messages.single()
        assertTrue(message.isMine)
        assertFalse(message.fromCommittee)
        assertEquals("https://x/api/v1/support-tickets/messages/9/attachment", message.attachmentUrl)
    }

    @Test fun open_sends_multipart_with_screenshot() = runTest {
        var seen: HttpRequestData? = null
        val repo = repository(status = HttpStatusCode.Created, onRequest = { seen = it })

        repo.open("bug", "Blank page", "It is blank", byteArrayOf(1, 2, 3), "shot.jpg")

        val request = seen!!
        assertEquals("/api/v1/support-tickets", request.url.encodedPath)
        assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
        val body = bodyText(request)
        assertTrue("name=type" in body || "name=\"type\"" in body)
        assertTrue("name=subject" in body || "name=\"subject\"" in body)
        assertTrue("name=screenshot" in body || "name=\"screenshot\"" in body)
        assertTrue("shot.jpg" in body)
    }

    @Test fun open_without_screenshot_omits_the_part() = runTest {
        var seen: HttpRequestData? = null
        val repo = repository(status = HttpStatusCode.Created, onRequest = { seen = it })

        repo.open("question", "Trip?", "Can my spouse join?", null, null)

        assertFalse("screenshot" in bodyText(seen!!))
    }

    @Test fun reply_posts_the_body_and_decodes_the_message() = runTest {
        var seen: HttpRequestData? = null
        val repo = repository(
            status = HttpStatusCode.Created,
            body = """{"data":{"id":10,"body":"Thanks","from_committee":false,"is_mine":true,"created_at":"2026-10-26T11:00:00+08:00","attachment_url":null}}""".encodeToByteArray(),
            onRequest = { seen = it },
        )

        val message = repo.reply(7, "Thanks")

        assertEquals("/api/v1/support-tickets/7/replies", seen?.url?.encodedPath)
        assertEquals(10L, message.id)
        assertNull(message.attachmentUrl)
    }

    @Test fun resolve_posts_to_the_resolve_endpoint() = runTest {
        var seen: HttpRequestData? = null
        repository(onRequest = { seen = it }).resolve(7)

        assertEquals("/api/v1/support-tickets/7/resolve", seen?.url?.encodedPath)
    }

    @Test fun attachment_sends_the_bearer_token_and_returns_bytes() = runTest {
        var seen: HttpRequestData? = null
        val png = byteArrayOf(-119, 80, 78, 71)
        val repo = repository(body = png, contentType = "image/png", onRequest = { seen = it })

        val bytes = repo.attachment("https://x/api/v1/support-tickets/messages/9/attachment")

        assertEquals("/api/v1/support-tickets/messages/9/attachment", seen?.url?.encodedPath)
        assertEquals("Bearer t", seen?.headers?.get(HttpHeaders.Authorization))
        assertContentEquals(png, bytes)
    }

    @Test fun a_404_surfaces_as_api_exception() = runTest {
        val repo = repository(status = HttpStatusCode.NotFound, body = """{"message":"Not Found"}""".encodeToByteArray())

        val failure = assertFailsWith<ApiException> { repo.ticket(99) }
        assertEquals(404, failure.status)
    }

    @Test fun new_fields_default_when_an_older_server_omits_them() {
        val dashboard = json.decodeFromString<DashboardDto>("""{"bills":{"total_outstanding":"0.00","available_credit":"0.00","unpaid_count":0}}""")
        val user = json.decodeFromString<UserDto>("""{"id":1,"name":"A","email":"a@x"}""")
        val notification = json.decodeFromString<NotificationDto>("""{"id":"u","title":"T"}""")

        assertEquals(0, dashboard.supportRepliesWaiting)
        assertFalse(user.isAdmin)
        assertNull(notification.supportTicketId)
    }

    @Test fun new_fields_decode_when_present() {
        val dashboard = json.decodeFromString<DashboardDto>(
            """{"bills":{"total_outstanding":"0.00","available_credit":"0.00","unpaid_count":0},"support_replies_waiting":2}""",
        )
        val user = json.decodeFromString<UserDto>("""{"id":1,"name":"A","email":"a@x","is_admin":true}""")
        val notification = json.decodeFromString<NotificationDto>("""{"id":"u","title":"T","support_ticket_id":7}""")

        assertEquals(2, dashboard.supportRepliesWaiting)
        assertTrue(user.isAdmin)
        assertEquals(7L, notification.supportTicketId)
    }
}
