package my.silentmode.pentana.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.relativeTimeFrom
import my.silentmode.pentana.shared.model.NotificationDto
import my.silentmode.pentana.shared.presentation.NotifUiState
import my.silentmode.pentana.shared.presentation.NotificationKind
import my.silentmode.pentana.shared.presentation.notificationKind
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.EmptyState
import my.silentmode.pentana.ui.components.LeadingIcon
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.BtnVariant
import my.silentmode.pentana.ui.components.PentFilledCard
import my.silentmode.pentana.ui.theme.LocalPentanaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(onMarkAllRead: () -> Unit, onDismiss: () -> Unit) {
    val vm = appViewModel { NotificationsViewModel(it.notifications) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Opening the list marks everything read and clears the bell badge.
    LaunchedEffect(Unit) { onMarkAllRead() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", style = MaterialTheme.typography.titleLarge)
                PentButton("Mark all read", onMarkAllRead, variant = BtnVariant.Text)
            }
            when (val uiState = state) {
                is NotifUiState.Loading -> Box(Modifier.fillMaxWidth().height(220.dp)) { LoadingState() }
                is NotifUiState.Error -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(uiState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is NotifUiState.Content -> if (uiState.items.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Notifications,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        title = "No notifications yet",
                    )
                } else {
                    PentFilledCard {
                        uiState.items.forEachIndexed { index, notification -> NotifRow(notification, last = index == uiState.items.lastIndex) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifRow(notification: NotificationDto, last: Boolean) {
    val unread = !notification.read
    val (icon, container, tint) = notifVisual(notification.title)
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(if (unread) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                LeadingIcon(icon, container, tint, size = 40.dp, iconSize = 20.dp, radius = 12.dp)
                if (unread) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopStart))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        notification.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    Text(relativeTimeFrom(notification.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                notification.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Map the shared kind decision to this platform's icon + colours. */
@Composable
private fun notifVisual(title: String): Triple<ImageVector, Color, Color> {
    val colors = LocalPentanaColors.current
    return when (notificationKind(title)) {
        NotificationKind.Lunch -> Triple(Icons.Filled.Restaurant, colors.lunch.container, colors.lunch.color)
        NotificationKind.Cancelled -> Triple(Icons.Filled.Cancel, colors.bad.container, colors.bad.color)
        NotificationKind.Payment -> Triple(Icons.Filled.Description, colors.proof.container, colors.proof.color)
        NotificationKind.ActivityJoined -> Triple(Icons.Filled.Celebration, colors.activ.container, colors.activ.color)
        NotificationKind.Activity -> Triple(Icons.Filled.CalendarMonth, colors.activ.container, colors.activ.color)
        NotificationKind.General -> Triple(Icons.Filled.Notifications, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
