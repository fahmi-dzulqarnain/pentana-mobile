package my.silentmode.pentana.feature.support

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.relativeTimeFrom
import my.silentmode.pentana.shared.model.SupportTicketDto
import my.silentmode.pentana.shared.presentation.SupportCreateState
import my.silentmode.pentana.shared.presentation.SupportListUiState
import my.silentmode.pentana.shared.presentation.SupportStatus
import my.silentmode.pentana.shared.presentation.supportListLabel
import my.silentmode.pentana.shared.presentation.supportStatus
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.ChipKind
import my.silentmode.pentana.ui.components.EmptyState
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LeadingIcon
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.PentFilledCard
import my.silentmode.pentana.ui.components.PentListItem
import my.silentmode.pentana.ui.components.StatusChip
import my.silentmode.pentana.ui.theme.LocalPentanaColors

/** "Ask the committee": the member's questions, Open / Closed, with a FAB for a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportListScreen(onBack: () -> Unit, onOpenTicket: (Long) -> Unit) {
    val vm = appViewModel { SupportListViewModel(it.support) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val create by vm.store.create.collectAsStateWithLifecycle()
    var showClosed by rememberSaveable { mutableStateOf(false) }
    var showNew by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    // Back from a conversation: its reply or resolve may have changed a status.
    LaunchedEffect(Unit) { vm.refresh() }
    // A new question opens its conversation straight away.
    LaunchedEffect(create) {
        (create as? SupportCreateState.Sent)?.let { sent ->
            showNew = false
            vm.store.resetCreate()
            onOpenTicket(sent.ticketId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask the committee") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (!showClosed) {
                ExtendedFloatingActionButton(
                    onClick = { showNew = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Filled.Edit, null) },
                    text = { Text("New question") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(pad)) {
            when (val uiState = state) {
                is SupportListUiState.Loading -> LoadingState()
                is SupportListUiState.Error -> ErrorState(uiState.message, vm.store::load)
                is SupportListUiState.Content -> {
                    val tickets = if (showClosed) uiState.closed else uiState.open
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            FilterChip(selected = !showClosed, onClick = { showClosed = false }, label = { Text("Open") })
                            FilterChip(selected = showClosed, onClick = { showClosed = true }, label = { Text("Closed") })
                        }
                        if (tickets.isEmpty()) {
                            EmptyState(
                                icon = Icons.Filled.SupportAgent,
                                iconColor = LocalPentanaColors.current.lunch.color,
                                container = LocalPentanaColors.current.lunch.container,
                                title = if (showClosed) "No closed questions" else "No questions yet",
                                body = if (showClosed) null else "Ask the committee about payments, lunch, activities or the app.",
                            )
                        } else {
                            PentFilledCard {
                                tickets.forEachIndexed { index, ticket ->
                                    TicketRow(ticket, showDivider = index < tickets.lastIndex, onClick = { onOpenTicket(ticket.id) })
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "The committee usually replies within a few days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewSupportTicketSheet(store = vm.store, onDismiss = { showNew = false; vm.store.resetCreate() })
    }
}

@Composable
private fun TicketRow(ticket: SupportTicketDto, showDivider: Boolean, onClick: () -> Unit) {
    val status = supportStatus(ticket.status)
    val who = if (ticket.lastMessageFromCommittee) "Committee" else "You"
    val whenText = relativeTimeFrom(ticket.lastMessageAt)
    PentListItem(
        headline = ticket.subject,
        supporting = if (whenText.isBlank()) who else "$who · $whenText",
        leading = { SupportTypeIcon(ticket.type) },
        trailing = { SupportStatusChip(status, supportListLabel(status)) },
        showDivider = showDivider,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The ticket type's icon tile, shared by the list and the conversation. */
@Composable
fun SupportTypeIcon(type: String) {
    val colors = LocalPentanaColors.current
    when (type) {
        "bug" -> LeadingIcon(Icons.Filled.BugReport, colors.bad.container, colors.bad.color)
        "suggestion" -> LeadingIcon(Icons.Filled.Lightbulb, colors.activ.container, colors.activ.color)
        else -> LeadingIcon(Icons.Filled.QuestionAnswer, colors.dues.container, colors.dues.color)
    }
}

/** The shared status mapped to this platform's chip styling. */
@Composable
fun SupportStatusChip(status: SupportStatus, label: String) {
    when (status) {
        SupportStatus.REPLIED -> StatusChip(ChipKind.VoteNow, label = label) // warn colours + dot
        SupportStatus.CLOSED -> StatusChip(ChipKind.Closed, label = label)
        // Open is the dues blue, matching iOS (StatusChip's own "Open" is the activities green).
        SupportStatus.OPEN -> Text(
            label,
            color = LocalPentanaColors.current.dues.color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(LocalPentanaColors.current.dues.container, RoundedCornerShape(8.dp))
                .padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}
