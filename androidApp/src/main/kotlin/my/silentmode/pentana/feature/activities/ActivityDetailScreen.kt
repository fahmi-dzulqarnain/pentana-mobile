package my.silentmode.pentana.feature.activities

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.dateTimeMedium
import my.silentmode.pentana.shared.presentation.ActivitiesUiState
import my.silentmode.pentana.shared.presentation.activityCardState
import my.silentmode.pentana.shared.presentation.activityDetailBlocks
import my.silentmode.pentana.shared.presentation.findActivity
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.EmptyState
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.theme.LocalPentanaColors

/**
 * One activity: title, when and where, the full description (text, links, images), and the same
 * register / cancel / waitlist action as its card. Reads the activity from the shared
 * [ActivitiesViewModel] (same session-scoped instance as the list), so an action taken here
 * updates both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(activityId: Long, onBack: () -> Unit) {
    val vm = appViewModel { ActivitiesViewModel(it.activities) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    val actionError by vm.store.actionError.collectAsStateWithLifecycle()
    var registering by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.store.dismissActionError()
        }
    }
    BackHandler(onBack = onBack)

    val activity = findActivity(state, activityId)
    val colors = LocalPentanaColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (activity != null) {
                val busy = activity.id in inFlight
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).alpha(if (busy) 0.6f else 1f)) {
                        ActionRow(
                            activity = activity,
                            cardState = activityCardState(activity),
                            busy = busy,
                            onRegister = { tapped ->
                                if (tapped.questions.isNotEmpty()) { vm.store.resetReg(); registering = true } else vm.store.quickRegister(tapped.id)
                            },
                            onCancel = { vm.store.cancel(it) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                activity != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Text(activity.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        SpotsChip(activity, activityCardState(activity))
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconLine(Icons.Filled.CalendarToday, dateTimeMedium(activity.startsAt), colors.activ.color)
                        activity.location?.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.Place, it, colors.activ.color) }
                    }
                    val blocks = remember(activity.fullDescription, activity.overview, activity.description) { activityDetailBlocks(activity) }
                    if (blocks.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        RichTextContent(blocks)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                state is ActivitiesUiState.Loading -> LoadingState()
                state is ActivitiesUiState.Error -> ErrorState((state as ActivitiesUiState.Error).message, vm.store::load)
                else -> EmptyState(
                    icon = Icons.Filled.CalendarMonth,
                    iconColor = colors.activ.color,
                    container = colors.activ.container,
                    title = "Activity not found",
                    body = "It may have been removed. Go back to see the latest list.",
                )
            }
        }
    }

    if (registering && activity != null) {
        RegistrationSheet(activity = activity, store = vm.store, onDismiss = { registering = false; vm.store.resetReg() })
    }
}
