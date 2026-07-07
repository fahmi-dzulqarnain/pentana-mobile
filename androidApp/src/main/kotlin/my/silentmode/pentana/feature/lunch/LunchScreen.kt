package my.silentmode.pentana.feature.lunch

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.dateTimeMedium
import my.silentmode.pentana.shared.model.LunchDto
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.LunchStore
import my.silentmode.pentana.shared.presentation.LunchUiState
import my.silentmode.pentana.shared.presentation.lunchClosedSummary
import my.silentmode.pentana.shared.presentation.lunchStatus
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.ChipKind
import my.silentmode.pentana.ui.components.EmptyState
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.PentFilledCard
import my.silentmode.pentana.ui.components.SingleSelectRow
import my.silentmode.pentana.ui.components.StatusChip
import my.silentmode.pentana.ui.theme.LocalPentanaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunchScreen() {
    val vm = appViewModel { LunchViewModel(it.lunch) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm.store::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is LunchUiState.Loading -> LoadingState()
            is LunchUiState.Error -> ErrorState(s.message, vm.store::load)
            is LunchUiState.Content -> if (s.lunches.isEmpty()) LunchEmpty() else LunchList(s.lunches, inFlight, vm.store)
        }
    }
}

@Composable
private fun LunchEmpty() {
    val pc = LocalPentanaColors.current
    EmptyState(
        icon = Icons.Filled.Restaurant,
        iconColor = pc.lunch.color,
        container = pc.lunch.container,
        title = "No upcoming lunches",
        body = "New catered lunches show up here to vote on.",
    )
}

@Composable
private fun LunchList(lunches: List<LunchDto>, inFlight: Set<Long>, store: LunchStore) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lunches.forEach { lunch ->
            LunchCard(
                lunch = lunch,
                busy = lunch.id in inFlight,
                onChoose = { optionId -> store.choose(lunch.id, optionId) },
                onNotAttending = { store.notAttending(lunch.id) },
            )
        }
    }
}

@Composable
private fun LunchCard(lunch: LunchDto, busy: Boolean, onChoose: (Long) -> Unit, onNotAttending: () -> Unit) {
    PentFilledCard {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(lunch.menu ?: "Lunch", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(lunch.date, lunch.caterer).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(chipKind(lunchStatus(lunch)))
        }

        if (!lunch.isOpen) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(lunchClosedSummary(lunch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.alpha(if (busy) 0.6f else 1f)) {
                lunch.options.forEach { opt ->
                    SingleSelectRow(
                        title = opt.name ?: "Option",
                        selected = lunch.myMealOptionId == opt.mealOptionId,
                        onClick = { onChoose(opt.mealOptionId) },
                        showDivider = true,
                        enabled = !busy,
                    )
                }
                SingleSelectRow(
                    title = "Not attending",
                    selected = lunch.responded && lunch.myMealOptionId == null,
                    onClick = onNotAttending,
                    showDivider = false,
                    enabled = !busy,
                )
            }
        }

        if (lunch.deadline != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    (if (lunch.isOpen) "Order by " else "Closed ") + dateTimeMedium(lunch.deadline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun chipKind(status: LunchStatus): ChipKind = when (status) {
    LunchStatus.VoteNow -> ChipKind.VoteNow
    LunchStatus.Responded -> ChipKind.Responded
    LunchStatus.Closed -> ChipKind.Closed
}
