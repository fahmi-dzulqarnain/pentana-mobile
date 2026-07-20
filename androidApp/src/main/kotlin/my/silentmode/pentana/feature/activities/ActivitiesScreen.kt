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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.dateTimeMedium
import my.silentmode.pentana.core.excerpt
import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.presentation.ActivitiesUiState
import my.silentmode.pentana.shared.presentation.ActivityCardState
import my.silentmode.pentana.shared.presentation.activityCardState
import my.silentmode.pentana.shared.presentation.spotsLabel
import my.silentmode.pentana.shared.presentation.waitlistLabel
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
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    val actionError by vm.store.actionError.collectAsStateWithLifecycle()
    var sheetActivity by remember { mutableStateOf<ActivityDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.store.dismissActionError()
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val uiState = state) {
            is ActivitiesUiState.Loading -> LoadingState()
            is ActivitiesUiState.Error -> ErrorState(uiState.message, vm.store::load)
            is ActivitiesUiState.Content -> if (uiState.activities.isEmpty()) {
                ActivitiesEmpty()
            } else {
                ActivitiesList(
                    activities = uiState.activities,
                    inFlight = inFlight,
                    onRegister = { activity ->
                        if (activity.questions.isNotEmpty()) { vm.store.resetReg(); sheetActivity = activity } else vm.store.register(activity.id, emptyMap())
                    },
                    onCancel = { vm.store.cancel(it) },
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    sheetActivity?.let { activity ->
        RegistrationSheet(activity = activity, store = vm.store, onDismiss = { sheetActivity = null; vm.store.resetReg() })
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
private fun ActivitiesList(activities: List<ActivityDto>, inFlight: Set<Long>, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        activities.forEach { ActivityCard(it, busy = it.id in inFlight, onRegister, onCancel) }
    }
}

@Composable
private fun ActivityCard(activity: ActivityDto, busy: Boolean, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    val colors = LocalPentanaColors.current
    val cardState = activityCardState(activity)
    PentElevatedCard {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(activity.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SpotsChip(activity, cardState)
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
            Column(Modifier.alpha(if (busy) 0.6f else 1f)) {
                ActionRow(activity, cardState, busy, onRegister, onCancel)
            }
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
private fun SpotsChip(activity: ActivityDto, cardState: ActivityCardState) {
    when (cardState) {
        ActivityCardState.Registered -> StatusChip(ChipKind.Registered)
        ActivityCardState.Waitlisted -> StatusChip(ChipKind.Waitlisted, label = "Full")
        ActivityCardState.Open, ActivityCardState.Closed -> StatusChip(ChipKind.Open, label = spotsLabel(activity))
    }
}

@Composable
private fun ActionRow(activity: ActivityDto, cardState: ActivityCardState, busy: Boolean, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    val colors = LocalPentanaColors.current
    when (cardState) {
        ActivityCardState.Registered -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = colors.ok.color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("You're registered", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.ok.color)
            Spacer(Modifier.weight(1f))
            PentButton("Cancel registration", { onCancel(activity.id) }, variant = BtnVariant.Destructive, enabled = !busy)
        }
        ActivityCardState.Waitlisted -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, tint = colors.warn.color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text(
                waitlistLabel(activity),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.warn.color,
            )
            Spacer(Modifier.weight(1f))
            PentButton("Cancel", { onCancel(activity.id) }, variant = BtnVariant.Destructive, enabled = !busy)
        }
        ActivityCardState.Closed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text("Registration closed", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ActivityCardState.Open -> PentButton(
            "Register", { onRegister(activity) },
            modifier = Modifier.fillMaxWidth(),
            variant = BtnVariant.Filled,
            enabled = !busy,
        )
    }
}
