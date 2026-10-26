package my.silentmode.pentana.feature.support

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.silentmode.pentana.core.PickedPhoto
import my.silentmode.pentana.core.readPhoto
import my.silentmode.pentana.shared.presentation.SupportCreateState
import my.silentmode.pentana.shared.presentation.SupportListStore
import my.silentmode.pentana.shared.presentation.supportTypeLabel
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.PentTextField
import my.silentmode.pentana.ui.components.PhotoPickerTile

/** New question form, the same pattern as SubmitProofSheet (filled fields, photo tile, full-width button). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSupportTicketSheet(store: SupportListStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val create by store.create.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf("question") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<PickedPhoto?>(null) }
    var photoError by remember { mutableStateOf(false) }
    val preview = remember(photo) { photo?.preview?.asImageBitmap() }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            // Same preparation as payment proofs: decoded, scaled and re-encoded as JPEG under 5 MB.
            val picked = withContext(Dispatchers.Default) { readPhoto(context, uri) }
            photo = picked
            photoError = picked == null
        }
    }

    val sending = create is SupportCreateState.Sending
    val ready = subject.isNotBlank() && message.isNotBlank() && subject.length <= 150 && message.length <= 5000 &&
        !sending && create !is SupportCreateState.Sent

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("New question", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("question", "bug", "suggestion").forEach { option ->
                    FilterChip(selected = type == option, onClick = { type = option }, label = { Text(supportTypeLabel(option)) })
                }
            }
            Spacer(Modifier.height(12.dp))
            PentTextField(
                value = subject,
                onValueChange = { subject = it },
                label = "Subject",
                placeholder = "e.g. Paid twice for October?",
                isError = subject.length > 150,
                supportingText = if (subject.length > 150) "Keep it under 150 characters." else null,
            )
            Spacer(Modifier.height(14.dp))
            PentTextField(
                value = message,
                onValueChange = { message = it },
                label = "Message",
                placeholder = "Tell the committee what's going on…",
                multiline = true,
                isError = message.length > 5000,
                supportingText = if (message.length > 5000) "Keep it under 5000 characters." else null,
            )
            Spacer(Modifier.height(14.dp))
            Text("Screenshot (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            PhotoPickerTile(
                selectedName = photo?.name,
                selectedSize = photo?.sizeLabel,
                preview = preview,
                onPick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onClear = { photo = null },
                emptyTitle = "Add screenshot",
            )
            if (photoError) {
                Spacer(Modifier.height(8.dp))
                Text("That image couldn't be read. Please choose another.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            (create as? SupportCreateState.Error)?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(error.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            PentButton(
                text = "Send to committee",
                onClick = { store.create(type, subject, message, photo?.bytes, photo?.name) },
                modifier = Modifier.fillMaxWidth(),
                enabled = ready,
                loading = sending,
            )
        }
    }
}
