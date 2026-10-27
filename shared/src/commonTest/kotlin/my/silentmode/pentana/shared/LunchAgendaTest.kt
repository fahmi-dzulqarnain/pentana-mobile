package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.LunchDto
import my.silentmode.pentana.shared.model.LunchMenuDto
import my.silentmode.pentana.shared.model.LunchMenuOptionDto
import my.silentmode.pentana.shared.presentation.LunchRowAction
import my.silentmode.pentana.shared.presentation.LunchRowKind
import my.silentmode.pentana.shared.presentation.LunchStore
import my.silentmode.pentana.shared.presentation.LunchUiState
import my.silentmode.pentana.shared.presentation.civilOf
import my.silentmode.pentana.shared.presentation.epochDayOf
import my.silentmode.pentana.shared.presentation.lunchAgenda
import my.silentmode.pentana.shared.presentation.lunchSheet
import my.silentmode.pentana.shared.presentation.parseIsoInstant
import my.silentmode.pentana.shared.presentation.weekdayIndex
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The week from the design mockups: it's Wednesday 28 Oct 2026, 10:00 in Malaysia (UTC+8). */
class LunchAgendaTest {
    private val myt = 8 * 3600
    private val wedTen = parseIsoInstant("2026-10-28T10:00:00+08:00")!!

    private val dapurMak = LunchMenuDto(1, "Dapur Mak", "Nasi Ayam Penyet", listOf(LunchMenuOptionDto(11, 101, "Regular"), LunchMenuOptionDto(12, 102, "Extra sambal")))
    private val greenBowl = LunchMenuDto(2, "Green Bowl", "Grilled Chicken Salad", listOf(LunchMenuOptionDto(21, 201, "Salad"), LunchMenuOptionDto(22, 202, "Wrap")))

    private fun lunch(id: Long, date: String, deadline: String, isOpen: Boolean, pick: Long? = null, responded: Boolean = pick != null) =
        LunchDto(id = id, date = date, deadline = deadline, isOpen = isOpen, menus = listOf(dapurMak, greenBowl), responded = responded, myLunchOptionId = pick)

    private val week = listOf(
        lunch(1, "2026-10-26", "2026-10-25T16:00:00+08:00", isOpen = false, pick = 12),
        lunch(2, "2026-10-27", "2026-10-26T16:00:00+08:00", isOpen = false),
        lunch(3, "2026-10-28", "2026-10-27T16:00:00+08:00", isOpen = false, pick = 21),
        lunch(4, "2026-10-29", "2026-10-28T16:00:00+08:00", isOpen = true),
        lunch(5, "2026-10-30", "2026-10-29T16:00:00+08:00", isOpen = true, pick = 11),
        lunch(6, "2026-11-02", "2026-11-01T16:00:00+08:00", isOpen = true),
    )

    @Test fun dates_round_trip_and_know_their_weekday() {
        val day = epochDayOf("2026-10-28")!!
        assertEquals(2, weekdayIndex(day)) // Wednesday
        assertEquals(29, civilOf(day + 1).day)
        assertEquals(11, civilOf(epochDayOf("2026-11-02")!!).month)
        assertEquals(0, weekdayIndex(epochDayOf("2024-02-26")!!)) // a Monday in a leap year
        assertEquals(civilOf(epochDayOf("2024-02-29")!!).day, 29)
        assertNull(epochDayOf("not a date"))
    }

    @Test fun iso_instants_honour_their_offset() {
        assertEquals(parseIsoInstant("2026-10-28T08:00:00Z"), parseIsoInstant("2026-10-28T16:00:00+08:00"))
        assertEquals(parseIsoInstant("2026-10-28T08:00:00Z"), parseIsoInstant("2026-10-28T08:00:00.000000Z"))
        assertNull(parseIsoInstant("2026-10-28"))
    }

    @Test fun banner_counts_pending_days_and_targets_the_soonest() {
        val banner = assertNotNull(lunchAgenda(week, wedTen, myt).banner)
        assertEquals("2 lunches need your vote", banner.title)
        assertEquals(listOf("Thu 29 Oct closes today 4:00 pm · in 6 h", "Mon 2 Nov closes Sun 1 Nov, 4:00 pm"), banner.lines)
        assertEquals("Vote for Thu", banner.actionLabel)
        assertEquals(4L, banner.targetLunchId)
    }

    @Test fun sections_split_this_week_from_next() {
        val agenda = lunchAgenda(week, wedTen, myt)
        assertEquals(listOf("This week", "Next week"), agenda.sections.map { it.title })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), agenda.sections[0].rows.map { it.lunchId })
        assertEquals(listOf(6L), agenda.sections[1].rows.map { it.lunchId })
    }

    @Test fun rows_say_what_each_day_needs() {
        val rows = lunchAgenda(week, wedTen, myt).sections.flatMap { it.rows }.associateBy { it.lunchId }

        rows.getValue(1).let {
            assertEquals(LunchRowKind.Past, it.kind)
            assertEquals("Extra sambal", it.title)
            assertEquals("Nasi Ayam Penyet", it.detail)
            assertEquals(LunchRowAction.View, it.action)
        }
        assertEquals("You didn't order", rows.getValue(2).title)

        rows.getValue(3).let {
            assertTrue(it.isToday)
            assertEquals("Today · You're having", it.eyebrow)
            assertEquals("Salad", it.title)
            assertEquals("Grilled Chicken Salad · Green Bowl", it.detail)
            assertEquals("WED", it.weekday)
            assertEquals("28", it.day)
        }

        rows.getValue(4).let {
            assertEquals(LunchRowKind.NeedsVote, it.kind)
            assertEquals("Not voted yet", it.title)
            assertEquals("Closes today 4:00 pm · in 6 h", it.subtitle)
            assertEquals("Nasi Ayam Penyet or Grilled Chicken Salad", it.menuPreview)
            assertTrue(it.urgent)
            assertEquals(LunchRowAction.Vote, it.action)
        }

        rows.getValue(5).let {
            assertEquals(LunchRowKind.Ordered, it.kind)
            assertEquals("Regular", it.title)
            assertEquals("Change by tomorrow 4:00 pm", it.subtitle)
            assertFalse(it.urgent)
            assertEquals(LunchRowAction.Change, it.action)
        }

        assertFalse(rows.getValue(6).urgent)
    }

    @Test fun countdown_switches_to_minutes_in_the_last_hour() {
        val threeFifteen = parseIsoInstant("2026-10-28T15:15:00+08:00")!!
        val thursday = lunchAgenda(week, threeFifteen, myt).sections[0].rows.first { it.lunchId == 4L }
        assertEquals("Closes today 4:00 pm · in 45 min", thursday.subtitle)
    }

    @Test fun a_deadline_passing_while_the_app_is_open_closes_the_day() {
        // The server still said is_open = true when the list was fetched.
        val fourOhOne = parseIsoInstant("2026-10-28T16:01:00+08:00")!!
        val agenda = lunchAgenda(week, fourOhOne, myt)
        val thursday = agenda.sections[0].rows.first { it.lunchId == 4L }
        assertEquals(LunchRowKind.Closed, thursday.kind)
        assertEquals("You didn't order", thursday.title)
        assertEquals("Ordering closed", thursday.subtitle)
        assertEquals("1 lunch needs your vote", agenda.banner?.title)
        assertEquals("Vote for Mon", agenda.banner?.actionLabel)
    }

    @Test fun no_banner_when_everything_is_answered() {
        val answered = week.map { if (it.isOpen && !it.responded) it.copy(responded = true, myLunchOptionId = 21) else it }
        assertNull(lunchAgenda(answered, wedTen, myt).banner)
    }

    @Test fun today_row_without_an_order_is_plain() {
        val today = lunchAgenda(listOf(lunch(3, "2026-10-28", "2026-10-27T16:00:00+08:00", isOpen = false)), wedTen, myt)
            .sections[0].rows.single()
        assertEquals("Today", today.eyebrow)
        assertEquals("You didn't order", today.title)
        assertNull(today.subtitle)
    }

    @Test fun sheet_for_an_open_day_lists_menus_and_offers_the_next_vote() {
        val friday = assertNotNull(lunchSheet(week, 5, wedTen, myt))
        assertEquals("Friday, 30 Oct", friday.title)
        assertEquals("Change by tomorrow 4:00 pm", friday.status)
        assertTrue(friday.isOpen)
        assertEquals(listOf("Dapur Mak · Nasi Ayam Penyet", "Green Bowl · Grilled Chicken Salad"), friday.menus.map { it.heading })
        assertTrue(friday.menus[0].options.first { it.lunchOptionId == 11L }.selected)
        assertEquals("Next: Thu 29 Oct", friday.next?.label)
        assertNull(friday.closedSummary)
    }

    @Test fun sheet_for_an_unanswered_day_waits_with_the_next_link() {
        val thursday = assertNotNull(lunchSheet(week, 4, wedTen, myt))
        assertEquals("Closes today 4:00 pm · in 6 h", thursday.status)
        assertTrue(thursday.urgent)
        assertNull(thursday.next) // only offered once this day is answered
    }

    @Test fun sheet_for_a_closed_day_is_read_only_with_the_summary() {
        val monday = assertNotNull(lunchSheet(week, 1, wedTen, myt))
        assertFalse(monday.isOpen)
        assertEquals("Ordering closed", monday.status)
        assertEquals("Ordering closed: you ordered Extra sambal (Nasi Ayam Penyet).", monday.closedSummary)
        assertNull(monday.next)
    }
}

class LunchStoreChangeTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun lunchJson(responded: Boolean, pick: Long?) = """
        {"id":5,"date":"2026-10-30","caterer":null,"menu":null,"deadline":"2026-10-29T16:00:00+08:00","is_open":true,"options":[],
        "menus":[{"id":1,"caterer":"Dapur Mak","menu":"Nasi Ayam Penyet","options":[
        {"lunch_option_id":11,"meal_option_id":101,"name":"Regular"},{"lunch_option_id":12,"meal_option_id":102,"name":"Extra sambal"}]}],
        "responded":$responded,"my_meal_option_id":null,"my_lunch_option_id":${pick ?: "null"}}
    """.trimIndent()

    /** A fake server that remembers the last answer it was sent. */
    private fun store(startResponded: Boolean, startPick: Long?, posted: MutableList<String>): LunchStore {
        var responded = startResponded
        var pick = startPick
        val engine = MockEngine { request ->
            val body = if (request.url.fullPath.endsWith("/respond")) {
                val sent = (request.body as io.ktor.http.content.TextContent).text
                posted += sent
                responded = true
                pick = Regex("\"lunch_option_id\":(\\d+)").find(sent)?.groupValues?.get(1)?.toLong()
                """{"data":${lunchJson(responded, pick)}}"""
            } else {
                """{"data":[${lunchJson(responded, pick)}]}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LunchStore(LunchRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun changing_a_pick_can_be_undone() = runTest {
        val posted = mutableListOf<String>()
        val store = store(startResponded = true, startPick = 11, posted)
        store.state.first { it is LunchUiState.Content }

        store.choose(5, 12)
        val change = store.lastChange.first { it != null }!!
        assertEquals("Changed Regular → Extra sambal", change.message)
        assertTrue(change.canUndo)

        store.undo()
        val undone = store.lastChange.first { it != null && it.seq > change.seq }!!
        assertEquals("Put back Regular", undone.message)
        assertFalse(undone.canUndo)
        assertTrue(posted.last().contains("\"lunch_option_id\":11"))
        val lunch = (store.state.value as LunchUiState.Content).lunches.single()
        assertEquals(11L, lunch.myLunchOptionId)
    }

    @Test fun a_first_answer_is_saved_without_undo() = runTest {
        val store = store(startResponded = false, startPick = null, mutableListOf())
        store.state.first { it is LunchUiState.Content }
        store.choose(5, 11)
        val change = store.lastChange.first { it != null }!!
        assertEquals("Saved · Fri 30 Oct: Regular", change.message)
        assertFalse(change.canUndo)
    }

    @Test fun tapping_the_saved_answer_again_sends_nothing() = runTest {
        val posted = mutableListOf<String>()
        val store = store(startResponded = true, startPick = 11, posted)
        store.state.first { it is LunchUiState.Content }
        store.choose(5, 11)
        assertTrue(posted.isEmpty())
        assertNull(store.lastChange.value)
    }

    @Test fun switching_to_not_attending_can_be_undone() = runTest {
        val posted = mutableListOf<String>()
        val store = store(startResponded = true, startPick = 12, posted)
        store.state.first { it is LunchUiState.Content }
        store.notAttending(5)
        val change = store.lastChange.first { it != null }!!
        assertEquals("Changed Extra sambal → Not attending", change.message)
        store.undo()
        store.lastChange.first { it != null && it.seq > change.seq }
        assertTrue(posted.last().contains("\"lunch_option_id\":12"))
    }
}
