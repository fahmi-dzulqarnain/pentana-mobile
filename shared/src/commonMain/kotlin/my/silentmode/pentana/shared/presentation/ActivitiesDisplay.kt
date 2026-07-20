package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.model.QuestionDto

/** Shared decision — each platform maps this to its own card styling. */
enum class ActivityCardState { Registered, Waitlisted, Open, Closed }

fun activityCardState(activity: ActivityDto): ActivityCardState = when {
    activity.myStatus == "registered" -> ActivityCardState.Registered
    activity.myStatus == "waitlisted" -> ActivityCardState.Waitlisted
    !activity.isOpen -> ActivityCardState.Closed
    else -> ActivityCardState.Open
}

/** Chip copy for an open activity's capacity (null spotsLeft = unlimited). */
fun spotsLabel(activity: ActivityDto): String {
    val spotsLeft = activity.spotsLeft ?: return "Open"
    return when {
        spotsLeft <= 0 -> "Full"
        spotsLeft == 1 -> "1 spot left"
        else -> "$spotsLeft spots left"
    }
}

fun waitlistLabel(activity: ActivityDto): String =
    activity.waitlistPosition?.let { position -> "Waitlisted — #$position" } ?: "Waitlisted"

/**
 * True when every required question is answered: non-blank for text/textarea/select, and
 * CHECKED for checkboxes (a required checkbox toggled off is not an answer — consent semantics).
 */
fun requiredAnswered(questions: List<QuestionDto>, answers: Map<String, String>): Boolean =
    questions.filter { it.required }.all { question ->
        val answer = answers[question.key] ?: ""
        if (question.type == "checkbox") answer == "true" else answer.isNotBlank()
    }

/** Checkbox answers are submitted as "true"/"false" strings (the documented API contract). */
fun checkboxValue(checked: Boolean): String = if (checked) "true" else "false"

/** The request payload: answered questions only — empty strings are unanswered, not answers. */
fun registrationPayload(answers: Map<String, String>): Map<String, String> =
    answers.filterValues { it.isNotEmpty() }

/** HTML description → plain-text blurb (tags stripped, entities/whitespace collapsed), null if empty. */
fun plainTextBlurb(html: String?): String? {
    if (html.isNullOrEmpty()) return null
    val plain = html
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([.,!?;:])"), "$1") // closing-tag spaces before punctuation, e.g. "</b>." → "shoes ." → "shoes."
        .trim()
    return plain.ifEmpty { null }
}
