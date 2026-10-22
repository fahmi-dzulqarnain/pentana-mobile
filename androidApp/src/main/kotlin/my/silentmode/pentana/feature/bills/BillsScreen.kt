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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import my.silentmode.pentana.core.dateTimeMedium
import my.silentmode.pentana.core.myr
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto
import my.silentmode.pentana.shared.model.PaymentProofDto
import my.silentmode.pentana.shared.presentation.BillStatus
import my.silentmode.pentana.shared.presentation.BillsUiState
import my.silentmode.pentana.shared.presentation.ProofStatus
import my.silentmode.pentana.shared.presentation.ReceiptState
import my.silentmode.pentana.shared.presentation.billStatus
import my.silentmode.pentana.shared.presentation.proofStatus
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

private fun billChip(status: BillStatus): ChipKind = when (status) {
    BillStatus.Paid -> ChipKind.Paid
    BillStatus.Partial -> ChipKind.Partial
    BillStatus.Overdue -> ChipKind.Overdue
    BillStatus.Unpaid -> ChipKind.Unpaid
}

private fun proofChip(status: ProofStatus): ChipKind = when (status) {
    ProofStatus.Pending -> ChipKind.ProofPending
    ProofStatus.Verified -> ChipKind.ProofVerified
    ProofStatus.Rejected -> ChipKind.ProofRejected
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen() {
    val vm = appViewModel { BillsViewModel(it.bills) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val receiptState by vm.store.receipt.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    // Each Ready/Error is handled once and handed back, so recomposition or rotation can't reopen it.
    // The snackbar runs in its own scope: consuming flips the key, which cancels this effect.
    LaunchedEffect(receiptState) {
        val message = when (val current = receiptState) {
            is ReceiptState.Ready -> when (openReceiptPdf(context, current.pdf)) {
                ReceiptOpenResult.Opened -> null
                ReceiptOpenResult.NoViewer -> "No app can open PDFs."
                ReceiptOpenResult.WriteFailed -> "Couldn't download the receipt. Please try again."
            }
            is ReceiptState.Error -> current.message
            else -> return@LaunchedEffect
        }
        vm.store.consumeReceipt()
        if (message != null) snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }
    // A download that finishes after the member has left Bills must not open a viewer later.
    DisposableEffect(Unit) { onDispose { vm.store.consumeReceipt() } }

    val downloadingReceiptId = (receiptState as? ReceiptState.Downloading)?.proofId

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when (val uiState = state) {
                is BillsUiState.Loading -> LoadingState()
                is BillsUiState.Error -> ErrorState(uiState.message, vm.store::load)
                is BillsUiState.Content -> BillsContent(
                    summary = uiState.summary,
                    bills = uiState.bills,
                    proofs = uiState.proofs,
                    downloadingReceiptId = downloadingReceiptId,
                    onResubmit = { showSheet = true },
                    onReceipt = vm.store::openReceipt,
                )
            }
        }
        SubmitFab(onClick = { showSheet = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (showSheet) {
        SubmitProofSheet(store = vm.store, onDismiss = { showSheet = false; vm.store.resetSubmit() })
    }
}

@Composable
private fun BillsContent(
    summary: BillsSummaryDto,
    bills: List<BillDto>,
    proofs: List<PaymentProofDto>,
    downloadingReceiptId: Long?,
    onResubmit: () -> Unit,
    onReceipt: (Long) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 96.dp),
    ) {
        SummaryCard(summary)
        // The member's own submissions, so a pending one is visible and a rejection's reason stays
        // readable after the push is gone. The Home "Payment proofs" card opens here.
        if (proofs.isNotEmpty()) {
            SectionHeader("Payment proofs")
            PentFilledCard {
                proofs.forEachIndexed { index, proof ->
                    ProofRow(
                        proof = proof,
                        last = index == proofs.lastIndex,
                        downloadingReceiptId = downloadingReceiptId,
                        onResubmit = onResubmit,
                        onReceipt = { onReceipt(proof.id) },
                    )
                }
            }
        }
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
    val status = billStatus(bill)
    val paid = status == BillStatus.Paid
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
                StatusChip(billChip(status))
            }
        },
        showDivider = !last,
    )
}

@Composable
private fun ProofRow(
    proof: PaymentProofDto,
    last: Boolean,
    downloadingReceiptId: Long?,
    onResubmit: () -> Unit,
    onReceipt: () -> Unit,
) {
    val colors = LocalPentanaColors.current
    val status = proofStatus(proof)
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LeadingIcon(Icons.Filled.Description, colors.proof.container, colors.proof.color, size = 40.dp, iconSize = 20.dp, radius = 12.dp)
            Column(Modifier.weight(1f)) {
                Money(myr(proof.amountClaimed), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                proof.submittedAt?.let { submitted ->
                    Text("Submitted ${dateTimeMedium(submitted)}", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
                val verifiedAmount = proof.amountVerified
                if (status == ProofStatus.Verified && verifiedAmount != null && verifiedAmount != proof.amountClaimed) {
                    Text("Verified as ${myr(verifiedAmount)}", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                }
                if (status == ProofStatus.Rejected) {
                    proof.adminNote?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            "Reason: $reason",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.bad.color,
                        )
                    }
                    TextButton(onClick = onResubmit) { Text("Submit a new proof") }
                }
                if (status == ProofStatus.Verified && proof.hasReceipt) {
                    ReceiptAction(
                        receiptNumber = proof.receiptNumber,
                        downloading = downloadingReceiptId == proof.id,
                        enabled = downloadingReceiptId == null,
                        onReceipt = onReceipt,
                    )
                }
            }
            StatusChip(proofChip(status))
        }
        if (!last) {
            HorizontalDivider(Modifier.padding(start = 78.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ReceiptAction(receiptNumber: String?, downloading: Boolean, enabled: Boolean, onReceipt: () -> Unit) {
    // The number sits on its own line so a narrow row can't wrap it mid-number.
    Column {
        if (downloading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Opening receipt…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            TextButton(onClick = onReceipt, enabled = enabled) { Text("View receipt") }
        }
        receiptNumber?.let { number ->
            Text(number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
