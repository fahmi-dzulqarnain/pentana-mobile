package my.silentmode.pentana.feature.home

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.firstName
import my.silentmode.pentana.core.myr
import my.silentmode.pentana.core.todayLong
import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.ChipKind
import my.silentmode.pentana.ui.components.DomainStatCard
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.Money
import my.silentmode.pentana.ui.components.NavDest
import my.silentmode.pentana.ui.components.PentElevatedCard
import my.silentmode.pentana.ui.components.LeadingIcon
import my.silentmode.pentana.ui.components.StatusChip
import my.silentmode.pentana.ui.theme.LocalPentanaColors
import my.silentmode.pentana.ui.theme.HeroShape
import my.silentmode.pentana.ui.theme.MoneyMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(userName: String, onSwitchTab: (NavDest) -> Unit) {
    val vm = appViewModel { HomeViewModel(it.dashboard) }
    val state by vm.state.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is HomeUiState.Loading -> LoadingState()
            is HomeUiState.Error -> ErrorState(s.message, vm::load)
            is HomeUiState.Content -> HomeContent(userName, s.data, onSwitchTab)
        }
    }
}

@Composable
private fun HomeContent(userName: String, d: DashboardDto, onSwitchTab: (NavDest) -> Unit) {
    val pc = LocalPentanaColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
    ) {
        // Expressive greeting hero
        Column(
            Modifier.fillMaxWidth().clip(HeroShape).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(todayLong(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
            Text("Hi, ${firstName(userName)}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(14.dp))

        val allClear = d.bills.totalOutstanding == "0.00" && d.pendingProofsCount == 0
        if (allClear) {
            PentElevatedCard {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LeadingIcon(Icons.Filled.Celebration, pc.ok.container, pc.ok.color, size = 46.dp, iconSize = 24.dp, radius = 23.dp)
                    Column {
                        Text("You're all clear", style = MaterialTheme.typography.titleLarge)
                        Text("No dues, nothing pending. Nice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            DomainStatCard(Icons.Filled.AccountBalanceWallet, pc.dues, "Dues", onClick = { onSwitchTab(NavDest.Bills) }) {
                if (d.bills.totalOutstanding == "0.00") {
                    Text("No dues outstanding", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Money("MYR ${d.bills.totalOutstanding}", style = MoneyMedium, color = pc.dues.color)
                        Text(" outstanding", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Credit ${myr(d.bills.availableCredit)} · ${d.bills.unpaidCount} unpaid",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        DomainStatCard(Icons.Filled.Restaurant, pc.lunch, "Next lunch", onClick = { onSwitchTab(NavDest.Lunch) }) {
            LunchSummary(d.nextLunch)
        }
        Spacer(Modifier.height(12.dp))

        DomainStatCard(Icons.Filled.CalendarMonth, pc.activ, "Activities", onClick = { onSwitchTab(NavDest.Activities) }) {
            ActivitySummary(d.nextActivity, d.openActivitiesCount)
        }
        Spacer(Modifier.height(12.dp))

        DomainStatCard(Icons.Filled.Description, pc.proof, "Payment proofs", onClick = { onSwitchTab(NavDest.Bills) }) {
            Text(
                if (d.pendingProofsCount > 0) "${d.pendingProofsCount} awaiting review" else "Nothing pending",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun LunchSummary(lunch: DashboardLunchDto?) {
    if (lunch == null) {
        Text("None scheduled", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        return
    }
    Text(
        listOfNotNull(lunch.date, lunch.menu).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
    )
    Spacer(Modifier.height(6.dp))
    when {
        lunch.isOpen && !lunch.responded -> StatusChip(ChipKind.VoteNow)
        lunch.responded -> StatusChip(ChipKind.Responded)
        else -> Text("Voting closed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivitySummary(activity: DashboardActivityDto?, openCount: Int) {
    if (activity == null) {
        Text("No upcoming registrations", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        if (openCount > 0) {
            Text("$openCount open to join", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Text(activity.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (activity.myStatus) {
            "registered" -> StatusChip(ChipKind.Registered)
            "waitlisted" -> StatusChip(ChipKind.Waitlisted)
            else -> {}
        }
        Text(
            (if (activity.myStatus == "registered" || activity.myStatus == "waitlisted") " · " else "") + "$openCount open",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
