package my.silentmode.pentana.feature.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.dateTimeMedium
import my.silentmode.pentana.core.excerpt
import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.BtnVariant
import my.silentmode.pentana.ui.components.ChipKind
import my.silentmode.pentana.ui.components.EmptyState
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.PentElevatedCard
import my.silentmode.pentana.ui.components.StatusChip
import my.silentmode.pentana.ui.theme.LocalPentanaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen() {
    val vm = appViewModel { ActivitiesViewModel(it.activities) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sheetActivity by remember { mutableStateOf<ActivityDto?>(null) }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val uiState = state) {
            is ActivitiesUiState.Loading -> LoadingState()
            is ActivitiesUiState.Error -> ErrorState(uiState.message, vm::load)
            is ActivitiesUiState.Content -> if (uiState.activities.isEmpty()) {
                ActivitiesEmpty()
            } else {
                ActivitiesList(
                    activities = uiState.activities,
                    onRegister = { activity ->
                        if (activity.questions.isNotEmpty()) { vm.resetReg(); sheetActivity = activity } else vm.register(activity.id, emptyMap())
                    },
                    onCancel = { vm.cancel(it) },
                )
            }
        }
    }

    sheetActivity?.let { activity ->
        RegistrationSheet(activity = activity, vm = vm, onDismiss = { sheetActivity = null; vm.resetReg() })
    }
}

@Composable
private fun ActivitiesEmpty() {
    val colors = LocalPentanaColors.current
    EmptyState(
        icon = Icons.Filled.CalendarMonth,
        iconColor = colors.activ.color,
        container = colors.activ.container,
        title = "No upcoming activities",
        body = "Check back soon for events to join.",
    )
}

@Composable
private fun ActivitiesList(activities: List<ActivityDto>, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        activities.forEach { ActivityCard(it, onRegister, onCancel) }
    }
}

@Composable
private fun ActivityCard(activity: ActivityDto, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    val colors = LocalPentanaColors.current
    PentElevatedCard {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(activity.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SpotsChip(activity)
            }
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                IconLine(Icons.Filled.CalendarToday, dateTimeMedium(activity.startsAt), colors.activ.color)
                activity.location?.let { IconLine(Icons.Filled.Place, it, colors.activ.color) }
            }
            activity.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(excerpt(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            ActionRow(activity, onRegister, onCancel)
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpotsChip(activity: ActivityDto) {
    when (activity.myStatus) {
        "registered" -> StatusChip(ChipKind.Registered)
        "waitlisted" -> StatusChip(ChipKind.Waitlisted, label = "Full")
        else -> {
            val left = activity.spotsLeft
            val label = when {
                left == null -> "Open"
                left <= 0 -> "Full"
                else -> "$left spots left"
            }
            StatusChip(ChipKind.Open, label = label)
        }
    }
}

@Composable
private fun ActionRow(activity: ActivityDto, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    val colors = LocalPentanaColors.current
    when {
        activity.myStatus == "registered" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = colors.ok.color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("You're registered", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.ok.color)
            Spacer(Modifier.weight(1f))
            PentButton("Cancel registration", { onCancel(activity.id) }, variant = BtnVariant.Destructive)
        }
        activity.myStatus == "waitlisted" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = colors.warn.color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text(
                activity.waitlistPosition?.let { "Waitlisted — #$it" } ?: "Waitlisted",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.warn.color,
            )
            Spacer(Modifier.weight(1f))
            PentButton("Cancel", { onCancel(activity.id) }, variant = BtnVariant.Destructive)
        }
        !activity.isOpen -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text("Registration closed", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> PentButton("Register", { onRegister(activity) }, modifier = Modifier.fillMaxWidth(), variant = BtnVariant.Filled)
    }
}
