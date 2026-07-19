package my.silentmode.pentana.feature.bills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import my.silentmode.pentana.core.PickedPhoto
import my.silentmode.pentana.core.readPhoto
import my.silentmode.pentana.shared.presentation.BillsStore
import my.silentmode.pentana.shared.presentation.SubmitState
import my.silentmode.pentana.shared.presentation.canSubmitProof
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.PentTextField
import my.silentmode.pentana.ui.components.PhotoPickerTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitProofSheet(store: BillsStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val submit by store.submit.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<PickedPhoto?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = readPhoto(context, uri)
    }

    LaunchedEffect(submit) {
        if (submit is SubmitState.Success) {
            delay(700)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Submit payment proof", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            PentTextField(
                value = amount,
                onValueChange = { amount = it },
                label = "Amount (MYR)",
                leadingIcon = Icons.Filled.Payments,
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
            )
            Spacer(Modifier.height(14.dp))
            PentTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note (optional)",
                placeholder = "Add a note for the reviewer",
                multiline = true,
            )
            Spacer(Modifier.height(14.dp))
            PhotoPickerTile(
                selectedName = photo?.name,
                selectedSize = photo?.sizeLabel,
                onPick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onClear = { photo = null },
            )

            (submit as? SubmitState.Error)?.let { submitError ->
                Spacer(Modifier.height(12.dp))
                Text(submitError.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            PentButton(
                text = if (submit is SubmitState.Success) "Submitted" else "Submit proof",
                onClick = { photo?.let { picked -> store.submitProof(picked.bytes, picked.name, amount, note.ifBlank { null }) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmitProof(amount, photo != null),
                loading = submit is SubmitState.Submitting,
            )
        }
    }
}
