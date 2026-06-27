package my.silentmode.pentana.feature.activities

import my.silentmode.pentana.shared.model.QuestionDto

/** True when every required question has a non-blank answer. */
fun requiredAnswered(questions: List<QuestionDto>, answers: Map<String, String>): Boolean =
    questions.filter { it.required }.all { (answers[it.key] ?: "").isNotBlank() }

/** Checkbox answers are submitted as "true"/"false" strings. */
fun checkboxValue(checked: Boolean): String = if (checked) "true" else "false"
