package my.silentmode.pentana.shared

import kotlinx.serialization.json.Json
import my.silentmode.pentana.shared.model.*
import my.silentmode.pentana.shared.presentation.HomeTodo
import my.silentmode.pentana.shared.presentation.dashboardLunchLabel
import my.silentmode.pentana.shared.presentation.dashboardLunchNames
import my.silentmode.pentana.shared.presentation.formatCents
import my.silentmode.pentana.shared.presentation.homeSections
import my.silentmode.pentana.shared.presentation.moneyCents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeDisplayTest {
    private fun lunch(id: Long = 1, isOpen: Boolean = true, responded: Boolean = false) =
        DashboardLunchDto(id = id, date = "2026-07-20", isOpen = isOpen, responded = responded)

    private fun dash(
        outstanding: String = "0.00",
        pending: String = "0.00",
        nextLunch: DashboardLunchDto? = null,
        otherLunch: DashboardLunchDto? = null,
        rejected: DashboardRejectedProofDto? = null,
        replies: Int = 0,
        ticket: DashboardSupportTicketDto? = null,
    ) = DashboardDto(
        bills = DashboardBillsDto(outstanding, "15.00", 2, pending),
        nextLunch = nextLunch, otherLunch = otherLunch, rejectedProof = rejected,
        supportRepliesWaiting = replies, supportReplyTicket = ticket,
    )

    @Test fun todos_follow_urgency_order() {
        val s = homeSections(
            dash(
                outstanding = "10.00", nextLunch = lunch(),
                rejected = DashboardRejectedProofDto(9, "5.00", "blurry"),
                replies = 1, ticket = DashboardSupportTicketDto(3, "Hi"),
            ),
        )
        assertEquals(
            listOf(HomeTodo.RejectedProof::class, HomeTodo.Dues::class, HomeTodo.LunchVote::class, HomeTodo.SupportReply::class),
            s.todos.map { it::class },
        )
    }

    @Test fun dues_fully_covered_by_pending_moves_to_proof_in_review() {
        val s = homeSections(dash("120.00", "120"))
        assertTrue(s.todos.none { it is HomeTodo.Dues })
        assertEquals("120.00", s.proofInReview)
    }

    @Test fun dues_partly_covered_shows_remainder() {
        val s = homeSections(dash("120.00", "50.00"))
        assertEquals(HomeTodo.Dues("70.00", 2, "15.00", "50.00"), s.todos.single())
        assertNull(s.proofInReview)
    }

    @Test fun dues_without_pending_has_no_in_review() {
        val s = homeSections(dash("120.00", "0.00"))
        assertEquals(HomeTodo.Dues("120.00", 2, "15.00", null), s.todos.single())
    }

    @Test fun pending_more_than_outstanding_is_still_in_review() {
        val s = homeSections(dash("40.00", "60.00"))
        assertTrue(s.todos.none { it is HomeTodo.Dues })
        assertEquals("60.00", s.proofInReview)
    }

    @Test fun zero_outstanding_has_no_dues_and_no_in_review() {
        val s = homeSections(dash("0.00", "0.00"))
        assertTrue(s.todos.isEmpty())
        assertNull(s.proofInReview)
    }

    @Test fun open_unvoted_lunch_is_a_todo_and_other_lunch_is_coming_up() {
        val next = lunch(1)
        val other = lunch(2, isOpen = false)
        val s = homeSections(dash(nextLunch = next, otherLunch = other))
        assertEquals(HomeTodo.LunchVote(next), s.todos.single())
        assertEquals(other, s.comingUpLunch)
    }

    @Test fun voted_lunch_is_coming_up_not_a_todo() {
        val next = lunch(responded = true)
        val s = homeSections(dash(nextLunch = next))
        assertTrue(s.todos.isEmpty())
        assertEquals(next, s.comingUpLunch)
    }

    @Test fun support_reply_todo_carries_oldest_ticket() {
        val s = homeSections(dash(replies = 2, ticket = DashboardSupportTicketDto(7, "Parking")))
        assertEquals(HomeTodo.SupportReply(2, 7, "Parking"), s.todos.single())
    }

    @Test fun rejected_proof_without_reason_shows_amount_only() {
        val s = homeSections(dash(rejected = DashboardRejectedProofDto(4, "25.00", null)))
        assertEquals(HomeTodo.RejectedProof(4, "25.00", null), s.todos.single())
    }

    @Test fun all_clear_when_only_pending_proof() {
        val s = homeSections(dash("30.00", "30.00"))
        assertTrue(s.todos.isEmpty())
        assertNotNull(s.proofInReview)
    }

    @Test fun parses_legacy_dashboard_payload() {
        // Same config as ApiClient's ContentNegotiation (Json instance there is not exposed).
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """
            {"bills":{"total_outstanding":"120.00","available_credit":"15.00","unpaid_count":2},
            "next_lunch":{"id":1,"date":"2026-07-20","caterer":"Dapur Selera","menu":"Nasi Lemak","is_open":true,"responded":false},
            "next_activity":{"id":5,"title":"Beach Cleanup","starts_at":"2026-07-25T08:00:00+08:00","my_status":"registered"},
            "open_activities_count":3,
            "pending_proofs_count":1}
        """.trimIndent()
        val s = homeSections(json.decodeFromString<DashboardDto>(legacy))
        assertEquals(2, s.todos.size)
        assertEquals("120.00", assertIs<HomeTodo.Dues>(s.todos[0]).amountDue)
        assertIs<HomeTodo.LunchVote>(s.todos[1])
    }

    @Test fun money_cents_round_trips() {
        assertEquals(12000, moneyCents("120"))
        assertEquals(12050, moneyCents("120.5"))
        assertEquals(12000, moneyCents("120.00"))
        assertEquals(50, moneyCents("0.5"))
        assertEquals(0, moneyCents("abc"))
        assertEquals("35.50", formatCents(3550))
    }

    @Test fun lunch_label_names_each_menu_else_its_caterer() {
        fun l(menus: List<DashboardMenuDto> = emptyList(), menu: String? = null, caterer: String? = null) =
            DashboardLunchDto(id = 1, date = "2026-07-20", menu = menu, caterer = caterer, isOpen = true, responded = false, menus = menus)

        assertEquals(
            "Lunch · Nasi lemak · Kak Mah",
            dashboardLunchLabel(l(listOf(DashboardMenuDto(menu = "Nasi lemak"), DashboardMenuDto(menu = " ", caterer = "Kak Mah")))),
        )
        assertEquals("Lunch · Soto", dashboardLunchLabel(l(menu = "Soto", caterer = "Kak Mah")))
        // A caterer-only lunch is named by its caterer, not a bare "Lunch".
        assertEquals("Lunch · Kak Mah", dashboardLunchLabel(l(caterer = "Kak Mah")))
        assertEquals("Lunch · Kak Mah", dashboardLunchLabel(l(menu = "", caterer = "Kak Mah")))
        assertEquals("Lunch", dashboardLunchLabel(l(listOf(DashboardMenuDto()))))
        assertEquals(emptyList(), dashboardLunchNames(l()))
    }
}
