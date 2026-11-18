package my.silentmode.pentana.shared

import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.model.QuestionDto
import my.silentmode.pentana.shared.presentation.ActivitiesUiState
import my.silentmode.pentana.shared.presentation.ActivityCardState
import my.silentmode.pentana.shared.presentation.RichBlock
import my.silentmode.pentana.shared.presentation.RichSpan
import my.silentmode.pentana.shared.presentation.activityDetailBlocks
import my.silentmode.pentana.shared.presentation.activityOverview
import my.silentmode.pentana.shared.presentation.activityCardState
import my.silentmode.pentana.shared.presentation.checkboxValue
import my.silentmode.pentana.shared.presentation.findActivity
import my.silentmode.pentana.shared.presentation.plainTextBlurb
import my.silentmode.pentana.shared.presentation.registrationPayload
import my.silentmode.pentana.shared.presentation.requiredAnswered
import my.silentmode.pentana.shared.presentation.spotsLabel
import my.silentmode.pentana.shared.presentation.waitlistLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivitiesDisplayTest {

    // Card state (order: registered → waitlisted → closed → open; matches both platforms today)

    @Test fun card_state_registered_wins() {
        assertEquals(ActivityCardState.Registered, activityCardState(activity(myStatus = "registered", isOpen = false)))
    }

    @Test fun card_state_waitlisted() {
        assertEquals(ActivityCardState.Waitlisted, activityCardState(activity(myStatus = "waitlisted", isOpen = true)))
    }

    @Test fun card_state_closed_when_not_open() {
        assertEquals(ActivityCardState.Closed, activityCardState(activity(myStatus = "none", isOpen = false)))
    }

    @Test fun card_state_open() {
        assertEquals(ActivityCardState.Open, activityCardState(activity(myStatus = "none", isOpen = true)))
    }

    // Spots label (union semantics: null=unlimited, zero-guard, singular/plural)

    @Test fun spots_label_cases() {
        assertEquals("Open", spotsLabel(activity(spotsLeft = null)))
        assertEquals("Full", spotsLabel(activity(spotsLeft = 0)))
        assertEquals("Full", spotsLabel(activity(spotsLeft = -1)))
        assertEquals("1 spot left", spotsLabel(activity(spotsLeft = 1)))
        assertEquals("4 spots left", spotsLabel(activity(spotsLeft = 4)))
    }

    // Waitlist label (unified: no " in line" suffix)

    @Test fun waitlist_label_with_and_without_position() {
        assertEquals("Waitlisted #3", waitlistLabel(activity(waitlistPosition = 3)))
        assertEquals("Waitlisted", waitlistLabel(activity(waitlistPosition = null)))
    }

    // Registration form helpers

    @Test fun required_text_must_be_non_blank() {
        val questions = listOf(QuestionDto(key = "name", label = "Name", required = true))
        assertFalse(requiredAnswered(questions, emptyMap()))
        assertFalse(requiredAnswered(questions, mapOf("name" to "   ")))
        assertTrue(requiredAnswered(questions, mapOf("name" to "Aisyah")))
    }

    @Test fun required_checkbox_must_be_checked() {
        val questions = listOf(QuestionDto(key = "consent", label = "Consent", type = "checkbox", required = true))
        assertFalse(requiredAnswered(questions, emptyMap()))
        assertFalse(requiredAnswered(questions, mapOf("consent" to "false"))) // toggled off is NOT answered
        assertTrue(requiredAnswered(questions, mapOf("consent" to "true")))
    }

    @Test fun optional_questions_never_block() {
        val questions = listOf(QuestionDto(key = "diet", label = "Diet", required = false))
        assertTrue(requiredAnswered(questions, emptyMap()))
    }

    @Test fun checkbox_encodes_documented_wire_format() {
        assertEquals("true", checkboxValue(true))
        assertEquals("false", checkboxValue(false))
    }

    @Test fun payload_drops_empty_answers() {
        assertEquals(
            mapOf("name" to "Aisyah", "consent" to "false"),
            registrationPayload(mapOf("name" to "Aisyah", "diet" to "", "consent" to "false")),
        )
    }

    // Blurb

    @Test fun blurb_strips_html_and_collapses_whitespace() {
        assertEquals("Bring water and shoes.", plainTextBlurb("<p>Bring   water</p> and&nbsp;<b>shoes</b>."))
    }

    @Test fun blurb_is_null_for_empty_input() {
        assertNull(plainTextBlurb(null))
        assertNull(plainTextBlurb(""))
        assertNull(plainTextBlurb("<p>  </p>"))
    }

    // Overview + detail body

    @Test fun overview_prefers_the_new_field() {
        assertEquals("Short overview", activityOverview(activity(description = "<p>Legacy</p>", overview = "  Short overview ")))
    }

    @Test fun overview_falls_back_to_stripped_legacy_description() {
        assertEquals("Bring water.", activityOverview(activity(description = "<p>Bring <b>water</b>.</p>")))
        assertEquals("Bring water.", activityOverview(activity(description = "<p>Bring <b>water</b>.</p>", overview = "  ")))
        assertNull(activityOverview(activity()))
    }

    @Test fun detail_blocks_use_the_full_description() {
        assertEquals(
            listOf(RichBlock.Paragraph(listOf(RichSpan("Full", bold = true))), RichBlock.Image("https://example.test/a.jpg")),
            activityDetailBlocks(activity(overview = "Short", fullDescription = "<p><strong>Full</strong></p><img src=\"https://example.test/a.jpg\">")),
        )
    }

    @Test fun detail_blocks_fall_back_to_the_overview() {
        val expected = listOf(RichBlock.Paragraph(listOf(RichSpan("Short"))), RichBlock.Paragraph(listOf(RichSpan("More"))))
        assertEquals(expected, activityDetailBlocks(activity(overview = "Short\n\nMore", fullDescription = null)))
        assertEquals(expected, activityDetailBlocks(activity(overview = "Short\n\nMore", fullDescription = "<p> </p>")))
        assertEquals(
            listOf(RichBlock.Paragraph(listOf(RichSpan("Legacy text")))),
            activityDetailBlocks(activity(description = "<p>Legacy <em>text</em></p>")),
        )
        assertEquals(emptyList(), activityDetailBlocks(activity()))
    }

    @Test fun find_activity_in_loaded_state_only() {
        val loaded = ActivitiesUiState.Content(listOf(activity(id = 1), activity(id = 2, myStatus = "registered")))
        assertEquals("registered", findActivity(loaded, 2)?.myStatus)
        assertNull(findActivity(loaded, 3))
        assertNull(findActivity(ActivitiesUiState.Loading, 1))
    }

    private fun activity(
        id: Long = 1,
        myStatus: String = "none",
        isOpen: Boolean = true,
        spotsLeft: Int? = null,
        waitlistPosition: Int? = null,
        description: String? = null,
        overview: String? = null,
        fullDescription: String? = null,
    ) = ActivityDto(
        id = id, title = "Beach Cleanup", description = description, overview = overview, fullDescription = fullDescription,
        startsAt = null, location = null,
        spotsLeft = spotsLeft, isOpen = isOpen, myStatus = myStatus, waitlistPosition = waitlistPosition,
        questions = emptyList(),
    )
}
