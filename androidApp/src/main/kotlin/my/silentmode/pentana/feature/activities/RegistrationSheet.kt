package my.silentmode.pentana.feature.activities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import my.silentmode.pentana.core.excerpt
import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.model.QuestionDto
import my.silentmode.pentana.ui.components.BtnVariant
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.PentCheckbox
import my.silentmode.pentana.ui.components.PentDropdown
import my.silentmode.pentana.ui.components.PentTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationSheet(activity: ActivityDto, vm: ActivitiesViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val answers = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(vm.reg) { if (vm.reg is RegState.Success) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
        ) {
            Text("Register", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            activity.description?.let {
                Text(
                    excerpt(it, max = 220),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(14.dp))
            }

            activity.questions.forEach { q ->
                QuestionField(q, answers)
                Spacer(Modifier.height(14.dp))
            }

            (vm.reg as? RegState.Error)?.let {
                Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
            }

            PentButton(
                text = "Register",
                onClick = { vm.register(activity.id, answers.toMap()) },
                modifier = Modifier.fillMaxWidth(),
                variant = BtnVariant.Filled,
                enabled = requiredAnswered(activity.questions, answers) && vm.reg !is RegState.Submitting,
                loading = vm.reg is RegState.Submitting,
            )
        }
    }
}

@Composable
private fun QuestionField(q: QuestionDto, answers: SnapshotStateMap<String, String>) {
    val label = q.label + if (q.required) " *" else ""
    when (q.type) {
        "textarea" -> PentTextField(answers[q.key] ?: "", { answers[q.key] = it }, label, multiline = true)
        "select" -> PentDropdown(label, answers[q.key] ?: "", q.options ?: emptyList(), { answers[q.key] = it }, placeholder = "Select")
        "checkbox" -> PentCheckbox(q.label, checked = answers[q.key] == "true", onCheckedChange = { answers[q.key] = checkboxValue(it) })
        else -> PentTextField(answers[q.key] ?: "", { answers[q.key] = it }, label)
    }
}
