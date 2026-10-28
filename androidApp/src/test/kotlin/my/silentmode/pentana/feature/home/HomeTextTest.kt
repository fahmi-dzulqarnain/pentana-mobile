package my.silentmode.pentana.feature.home

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import my.silentmode.pentana.shared.model.DashboardLunchDto
import my.silentmode.pentana.shared.model.DashboardMenuDto
import my.silentmode.pentana.shared.presentation.HomeTodo

class HomeTextTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")

    private fun lunch(menus: List<DashboardMenuDto> = emptyList(), menu: String? = null, caterer: String? = null, deadline: String? = null) =
        DashboardLunchDto(id = 1, date = "2026-10-30", caterer = caterer, menu = menu, isOpen = true, responded = false, menus = menus, voteDeadline = deadline)

    @Test fun rejected_proof_with_and_without_reason() {
        assertEquals("MYR 60.00 · Blurry photo", todoText(HomeTodo.RejectedProof(1, "60.00", " Blurry photo ")).detail)
        assertEquals("MYR 60.00", todoText(HomeTodo.RejectedProof(1, "60.00", null)).detail)
    }

    @Test fun dues_detail_bills_and_credit() {
        val text = todoText(HomeTodo.Dues("120.00", 2, "15.00", null))
        assertEquals("MYR 120.00 due", text.title)
        assertEquals("2 bills · MYR 15.00 credit", text.detail)
        assertEquals("Pay & upload", text.cue)
    }

    @Test fun dues_detail_omits_zero_credit() =
        assertEquals("1 bill", todoText(HomeTodo.Dues("50.00", 1, "0.00", null)).detail)

    @Test fun dues_detail_in_review_replaces_credit() =
        assertEquals("2 bills · MYR 30.00 in review", todoText(HomeTodo.Dues("90.00", 2, "15.00", "30.00")).detail)

    @Test fun dues_detail_never_blank() =
        assertEquals("0 bills", todoText(HomeTodo.Dues("10.00", 0, "0.00", null)).detail)

    @Test fun lunch_vote_two_menus_and_deadline() {
        val text = todoText(
            HomeTodo.LunchVote(lunch(listOf(DashboardMenuDto(menu = "Nasi lemak"), DashboardMenuDto(caterer = "Kak Mah")), deadline = "2026-10-29T02:00:00Z")),
            kl,
        )
        assertEquals("Vote for Fri lunch", text.title)
        assertEquals("2 menus · closes Thu", text.detail)
    }

    @Test fun lunch_vote_single_menu_uses_caterer_when_unnamed() =
        assertEquals("Kak Mah", todoText(HomeTodo.LunchVote(lunch(listOf(DashboardMenuDto(caterer = "Kak Mah")))), kl).detail)

    @Test fun lunch_vote_without_details() =
        assertEquals("Voting is open", todoText(HomeTodo.LunchVote(lunch()), kl).detail)

    @Test fun lunch_label_joins_with_dots() {
        assertEquals("Lunch · Nasi lemak · Kak Mah", lunchLabel(lunch(listOf(DashboardMenuDto(menu = "Nasi lemak"), DashboardMenuDto(caterer = "Kak Mah")))))
        assertEquals("Lunch · Soto", lunchLabel(lunch(menu = "Soto")))
        assertEquals("Lunch", lunchLabel(lunch()))
    }

    @Test fun support_reply_detail() {
        assertEquals("Can I pay in two parts?", todoText(HomeTodo.SupportReply(1, 7, "Can I pay in two parts?")).detail)
        assertEquals("1 ticket waiting on you", todoText(HomeTodo.SupportReply(1, 7, null)).detail)
        assertEquals("3 tickets waiting on you", todoText(HomeTodo.SupportReply(3, null, null)).detail)
    }

    @Test fun time_follows_12_and_24_hour() {
        val start = zoned("2026-11-07T12:00:00Z", kl)!!
        assertEquals("8:00 PM", timeOfDay(start, is24Hour = false))
        assertEquals("20:00", timeOfDay(start, is24Hour = true))
    }
}
