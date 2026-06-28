package my.silentmode.pentana

import my.silentmode.pentana.feature.activities.checkboxValue
import my.silentmode.pentana.feature.activities.requiredAnswered
import my.silentmode.pentana.shared.model.QuestionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegFormTest {
    private val questions = listOf(
        QuestionDto(key = "name", label = "Name", required = true),
        QuestionDto(key = "diet", label = "Diet", required = false),
    )

    @Test fun missing_required_is_incomplete() = assertFalse(requiredAnswered(questions, emptyMap()))

    @Test fun filled_required_is_complete() = assertTrue(requiredAnswered(questions, mapOf("name" to "Aisyah")))

    @Test fun blank_required_is_incomplete() = assertFalse(requiredAnswered(questions, mapOf("name" to "   ")))

    @Test fun checkbox_true_encodes() = assertEquals("true", checkboxValue(true))

    @Test fun checkbox_false_encodes() = assertEquals("false", checkboxValue(false))
}
