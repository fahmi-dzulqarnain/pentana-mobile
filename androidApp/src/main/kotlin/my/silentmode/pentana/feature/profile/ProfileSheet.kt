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
fun ProfileSheet(user: UserDto, onSignOut: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LocalPentanaColors.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Text(initials(user.name), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(user.name, style = MaterialTheme.typography.headlineSmall)
                Text(user.email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader("Membership")
            PentFilledCard {
                PentListItem(
                    headline = "Category",
                    trailing = { Text(user.memberCategory ?: "—", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    showDivider = true,
                )
                PentListItem(
                    headline = "Birthday",
                    trailing = { Text(user.birthday ?: "—", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
}
