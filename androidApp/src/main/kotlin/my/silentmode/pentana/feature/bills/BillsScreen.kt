package my.silentmode.pentana.feature.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.myr
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.ChipKind
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LeadingIcon
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.Money
import my.silentmode.pentana.ui.components.PentFilledCard
import my.silentmode.pentana.ui.components.PentListItem
import my.silentmode.pentana.ui.components.SectionHeader
import my.silentmode.pentana.ui.components.StatusChip
import my.silentmode.pentana.ui.components.SubmitFab
import my.silentmode.pentana.ui.theme.LocalPentanaColors
import my.silentmode.pentana.ui.theme.MoneyLarge

private fun billChip(status: String): ChipKind = when (status.lowercase()) {
    "paid" -> ChipKind.Paid
    "partial" -> ChipKind.Partial
    "overdue" -> ChipKind.Overdue
    else -> ChipKind.Unpaid
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen() {
    val vm = appViewModel { BillsViewModel(it.bills) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when (val uiState = state) {
                is BillsUiState.Loading -> LoadingState()
                is BillsUiState.Error -> ErrorState(uiState.message, vm::load)
                is BillsUiState.Content -> BillsContent(uiState.summary, uiState.bills)
            }
        }
        SubmitFab(onClick = { showSheet = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (showSheet) {
        SubmitProofSheet(vm = vm, onDismiss = { showSheet = false; vm.resetSubmit() })
    }
}

@Composable
private fun BillsContent(summary: BillsSummaryDto, bills: List<BillDto>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 96.dp),
    ) {
        SummaryCard(summary)
        if (bills.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No bills yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SectionHeader("Bill history")
            PentFilledCard {
                bills.forEachIndexed { index, bill -> BillRow(bill, last = index == bills.lastIndex) }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: BillsSummaryDto) {
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(20.dp),
    ) {
        Text("TOTAL OUTSTANDING", style = MaterialTheme.typography.labelLarge, color = onColor.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Money("MYR ${summary.totalOutstanding}", style = MoneyLarge, color = onColor)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Column {
                Money("MYR ${summary.availableCredit}", style = MaterialTheme.typography.headlineSmall, color = onColor)
                Text("Available credit", style = MaterialTheme.typography.bodySmall, color = onColor.copy(alpha = 0.85f))
            }
            Column {
                Money("${summary.unpaidCount}", style = MaterialTheme.typography.headlineSmall, color = onColor)
                Text("Unpaid bills", style = MaterialTheme.typography.bodySmall, color = onColor.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun BillRow(bill: BillDto, last: Boolean) {
    val colors = LocalPentanaColors.current
    val paid = bill.status.lowercase() == "paid"
    PentListItem(
        headline = bill.month,
        supporting = "Due ${myr(bill.amountDue)} · Paid ${myr(bill.amountPaid)}",
        leading = { LeadingIcon(Icons.Filled.ReceiptLong, colors.dues.container, colors.dues.color, size = 40.dp, iconSize = 20.dp, radius = 12.dp) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Money(
                    "MYR ${bill.outstanding}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(5.dp))
                StatusChip(billChip(bill.status))
            }
        },
        showDivider = !last,
    )
}
