package my.silentmode.pentana.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.model.avatarName
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.silentmode.pentana.core.initials
import my.silentmode.pentana.core.myr
import my.silentmode.pentana.shared.model.UserDto
import my.silentmode.pentana.ui.components.Money
import my.silentmode.pentana.ui.components.PentFilledCard
import my.silentmode.pentana.ui.components.PentListItem
import my.silentmode.pentana.ui.components.SectionHeader
import my.silentmode.pentana.ui.theme.LocalPentanaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(user: UserDto, onSaveNickname: suspend (String?) -> String?, onSignOut: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LocalPentanaColors.current
    var editingNickname by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Text(initials(user.avatarName), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(user.name, style = MaterialTheme.typography.headlineSmall)
                Text(user.email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader("Profile")
            PentFilledCard {
                Row(Modifier.fillMaxWidth().clickable { editingNickname = true }) {
                    PentListItem(
                        headline = "Nickname",
                        modifier = Modifier.weight(1f),
                        trailing = {
                            Text(user.nickname ?: "Not set", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Membership")
            PentFilledCard {
                PentListItem(
                    headline = "Category",
                    trailing = { Text(user.memberCategory ?: "-", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    showDivider = true,
                )
                PentListItem(
                    headline = "Birthday",
                    trailing = { Text(user.birthday ?: "-", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    showDivider = true,
                )
                PentListItem(
                    headline = "Credit balance",
                    trailing = {
                        Money(
                            myr(String.format(java.util.Locale.US, "%.2f", user.credit)),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ok.color,
                        )
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            PentFilledCard {
                Row(
                    Modifier.fillMaxWidth().clickable { onSignOut() }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    Text("Sign out", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (editingNickname) {
        NicknameDialog(
            current = user.nickname,
            onSave = onSaveNickname,
            onDismiss = { editingNickname = false },
        )
    }
}

private const val NICKNAME_MAX = 30

@Composable
private fun NicknameDialog(current: String?, onSave: suspend (String?) -> String?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit(value: String?) {
        saving = true
        error = null
        scope.launch {
            val message = onSave(value)
            saving = false
            if (message == null) onDismiss() else error = message
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Nickname") },
        text = {
            Column {
                Text("Shown in your greeting and avatar. Your full name stays on your profile.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= NICKNAME_MAX) text = it },
                    label = { Text("Nickname") },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null,
                    supportingText = { Text(error ?: "${text.length}/$NICKNAME_MAX") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { submit(null) }, enabled = !saving) { Text("Clear") }
                }
                TextButton(onClick = { submit(text.trim().ifEmpty { null }) }, enabled = !saving) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
