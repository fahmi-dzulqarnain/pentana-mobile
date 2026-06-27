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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.shared.model.LunchDto
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
    val state by vm.state.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is LunchUiState.Loading -> LoadingState()
            is LunchUiState.Error -> ErrorState(s.message, vm::load)
            is LunchUiState.Content -> if (s.lunches.isEmpty()) LunchEmpty() else LunchList(s.lunches, vm)
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
private fun LunchList(lunches: List<LunchDto>, vm: LunchViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lunches.forEach { lunch ->
            LunchCard(
                lunch = lunch,
                onChoose = { optionId -> vm.choose(lunch.id, optionId) },
                onNotAttending = { vm.notAttending(lunch.id) },
            )
        }
    }
}

@Composable
private fun LunchCard(lunch: LunchDto, onChoose: (Long) -> Unit, onNotAttending: () -> Unit) {
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
            StatusChip(lunchChip(lunch))
        }

        if (!lunch.isOpen) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(closedSummary(lunch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            lunch.options.forEach { opt ->
                SingleSelectRow(
                    title = opt.name ?: "Option",
                    selected = lunch.myMealOptionId == opt.mealOptionId,
                    onClick = { onChoose(opt.mealOptionId) },
                    showDivider = true,
                )
            }
            SingleSelectRow(
                title = "Not attending",
                selected = lunch.responded && lunch.myMealOptionId == null,
                onClick = onNotAttending,
                showDivider = false,
            )
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
                    (if (lunch.isOpen) "Order by " else "") + lunch.deadline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun lunchChip(lunch: LunchDto): ChipKind = when {
    lunch.isOpen && !lunch.responded -> ChipKind.VoteNow
    lunch.isOpen && lunch.responded -> ChipKind.Responded
    else -> ChipKind.Closed
}

private fun closedSummary(lunch: LunchDto): String {
    if (!lunch.responded) return "Ordering closed — no order placed."
    if (lunch.myMealOptionId == null) return "Ordering closed — you marked not attending."
    val name = lunch.options.firstOrNull { it.mealOptionId == lunch.myMealOptionId }?.name
    return if (name != null) "Ordering closed — you ordered $name." else "Ordering closed — order placed."
}
