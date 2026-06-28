package my.silentmode.pentana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class NavDest(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Bills("Bills", Icons.Filled.ReceiptLong),
    Lunch("Lunch", Icons.Filled.Restaurant),
    Activities("Activities", Icons.Filled.CalendarMonth),
}

@Composable
fun AvatarButton(initials: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.titleSmall)
    }
}

/** Large top app bar: avatar (lead) + badged bell (trail), then optional headline title. */
@Composable
fun PentTopBar(
    title: String?,
    unread: Int,
    avatarInitials: String,
    onAvatar: () -> Unit,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarButton(avatarInitials, onAvatar)
            Spacer(Modifier.weight(1f))
            BadgedBox(badge = {
                if (unread > 0) Badge { Text(if (unread > 9) "9+" else unread.toString()) }
            }) {
                IconButton(onClick = onBell) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications, $unread unread", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp))
        }
    }
}

@Composable
fun PentNavBar(active: NavDest, onSelect: (NavDest) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavDest.entries.forEach { d ->
            NavigationBarItem(
                selected = active == d,
                onClick = { onSelect(d) },
                icon = { Icon(d.icon, contentDescription = d.label) },
                label = { Text(d.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
