package my.silentmode.pentana.feature.lunch

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import my.silentmode.pentana.shared.presentation.LunchAgenda
import my.silentmode.pentana.shared.presentation.LunchAgendaRow
import my.silentmode.pentana.shared.presentation.LunchBanner
import my.silentmode.pentana.shared.presentation.LunchRowAction
import my.silentmode.pentana.shared.presentation.LunchRowKind
import my.silentmode.pentana.shared.presentation.LunchSheet
import my.silentmode.pentana.shared.presentation.LunchStore
import my.silentmode.pentana.shared.presentation.LunchUiState
import my.silentmode.pentana.shared.presentation.lunchAgenda
import my.silentmode.pentana.shared.presentation.lunchSheet
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.EmptyState
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.PentFilledCard
import my.silentmode.pentana.ui.components.SectionHeader
import my.silentmode.pentana.ui.components.SingleSelectRow
import my.silentmode.pentana.ui.theme.LocalPentanaColors
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunchScreen() {
    val vm = appViewModel { LunchViewModel(it.lunch) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    val actionError by vm.store.actionError.collectAsStateWithLifecycle()
    val lastChange by vm.store.lastChange.collectAsStateWithLifecycle()
    var sheetLunchId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val now = rememberMinuteClock()

    LaunchedEffect(actionError) {
        actionError?.let { message ->
            view.reject()
            snackbarHostState.showSnackbar(message)
            vm.store.dismissActionError()
        }
    }
    // One confirm haptic and one toast per saved pick; a newer save replaces the toast.
    LaunchedEffect(lastChange?.seq) {
        val change = lastChange ?: return@LaunchedEffect
        view.confirm()
        val result = snackbarHostState.showSnackbar(
            message = change.message,
            actionLabel = if (change.canUndo) "Undo" else null,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.store.undo()
        vm.store.dismissChange()
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val uiState = state) {
            is LunchUiState.Loading -> LoadingState()
            is LunchUiState.Error -> ErrorState(uiState.message, vm.store::load)
            is LunchUiState.Content -> if (uiState.lunches.isEmpty()) {
                LunchEmpty()
            } else {
                val agenda = remember(uiState.lunches, now) { lunchAgenda(uiState.lunches, now, offsetSeconds(now)) }
                LunchAgendaList(agenda, open = { sheetLunchId = it })
            }
        }
        // The sheet is its own window, so it hosts the snackbar while it's open.
        if (sheetLunchId == null) SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    val lunches = (state as? LunchUiState.Content)?.lunches.orEmpty()
    sheetLunchId?.let { lunchId ->
        ModalBottomSheet(
            onDismissRequest = { sheetLunchId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Box {
                val sheet = remember(lunches, lunchId, now) { lunchSheet(lunches, lunchId, now, offsetSeconds(now)) }
                if (sheet != null) {
                    LunchSheetContent(
                        sheet = sheet,
                        busy = lunchId in inFlight,
                        store = vm.store,
                        open = { sheetLunchId = it },
                        close = { sheetLunchId = null },
                    )
                }
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
    }
}

/** Epoch seconds, re-read on every minute boundary so countdowns tick and passed deadlines close. */
@Composable
private fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    return now
}

private fun offsetSeconds(epochSeconds: Long): Int = TimeZone.getDefault().getOffset(epochSeconds * 1000) / 1000

private fun View.confirm() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}

private fun View.reject() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

@Composable
private fun LunchEmpty() {
    val colors = LocalPentanaColors.current
    EmptyState(
        icon = Icons.Filled.Restaurant,
        iconColor = colors.lunch.color,
        container = colors.lunch.container,
        title = "No upcoming lunches",
        body = "New catered lunches show up here to vote on.",
    )
}

// ---- Agenda ----

@Composable
private fun LunchAgendaList(agenda: LunchAgenda, open: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        agenda.banner?.let { LunchBannerCard(it, open) }
        agenda.sections.forEach { section ->
            SectionHeader(section.title, Modifier.padding(start = 4.dp))
            PentFilledCard(Modifier.fillMaxWidth()) {
                section.rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LunchAgendaRowItem(row, onClick = { open(row.lunchId) })
                }
            }
        }
        Text(
            "No more lunches published yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
private fun LunchBannerCard(banner: LunchBanner, open: (Long) -> Unit) {
    val colors = LocalPentanaColors.current
    Surface(color = colors.lunch.container, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(banner.title, style = MaterialTheme.typography.titleSmall, color = colors.lunch.onContainer)
            banner.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.lunch.onContainer) }
            Button(
                onClick = { open(banner.targetLunchId) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.lunch.color, contentColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(top = 10.dp),
            ) { Text(banner.actionLabel) }
        }
    }
}

@Composable
private fun LunchAgendaRowItem(row: LunchAgendaRow, onClick: () -> Unit) {
    val colors = LocalPentanaColors.current
    val hint = when (row.action) {
        LunchRowAction.Vote -> "vote"
        LunchRowAction.Change -> "change your pick"
        LunchRowAction.View -> "see this day"
    }
    val rowModifier = Modifier.fillMaxWidth()
        .clickable(onClickLabel = hint, onClick = onClick)
        .clearAndSetSemantics {
            contentDescription = row.accessibilityLabel
            role = Role.Button
            onClick(label = hint) { onClick(); true }
        }
    if (row.kind == LunchRowKind.Past && !row.isToday) {
        // A day before today: one dimmed line.
        Row(rowModifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${row.weekday} ${row.day}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.width(52.dp))
            Text(
                buildAnnotatedString {
                    append(row.title)
                    row.detail?.let { withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) { append(" · $it") } }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
        return
    }
    val accent = row.isToday || row.kind == LunchRowKind.NeedsVote
    Row(
        rowModifier
            .background(if (row.isToday) colors.lunch.container else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = if (row.isToday) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(row.weekday, style = MaterialTheme.typography.labelSmall, color = if (accent) colors.lunch.color else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(row.day, style = MaterialTheme.typography.titleLarge, color = if (row.isToday) colors.lunch.color else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            row.eyebrow?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.lunch.color) }
            if (row.isToday) {
                Text(row.title, style = MaterialTheme.typography.titleLarge)
                row.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = if (row.kind == LunchRowKind.NeedsVote) FontWeight.SemiBold else FontWeight.Medium)) { append(row.title) }
                        row.detail?.let {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)) { append(" · $it") }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.kind == LunchRowKind.Closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            row.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (row.urgent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (row.urgent) colors.lunch.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.menuPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2) }
        }
        when (row.action) {
            LunchRowAction.Vote -> Surface(color = colors.lunch.color, contentColor = MaterialTheme.colorScheme.surface, shape = CircleShape) {
                Text("Vote", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LunchRowAction.Change -> Text("Change", style = MaterialTheme.typography.labelLarge, color = colors.lunch.color)
            LunchRowAction.View -> if (!row.isToday) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

// ---- Sheet ----

@Composable
private fun LunchSheetContent(sheet: LunchSheet, busy: Boolean, store: LunchStore, open: (Long) -> Unit, close: () -> Unit) {
    val colors = LocalPentanaColors.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(sheet.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    sheet.status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (sheet.urgent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sheet.urgent) colors.lunch.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = close) { Text("Close") }
        }

        if (sheet.isOpen) {
            PentFilledCard(Modifier.fillMaxWidth().alpha(if (busy) 0.6f else 1f)) {
                sheet.menus.forEachIndexed { index, menu ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuHeading(menu.heading)
                    menu.options.forEachIndexed { optionIndex, option ->
                        SingleSelectRow(
                            title = option.name,
                            selected = option.selected,
                            onClick = { store.choose(sheet.lunchId, option.lunchOptionId) },
                            showDivider = optionIndex < menu.options.lastIndex,
                            enabled = !busy,
                        )
                    }
                }
                // Older server (no menus): legacy options carry no lunchOptionId, so they are shown read-only rather than guessing an id.
                sheet.legacyOptions.forEach { option ->
                    SingleSelectRow(title = option.name, selected = option.selected, onClick = {}, showDivider = true, enabled = false)
                }
            }
            PentFilledCard(Modifier.fillMaxWidth().alpha(if (busy) 0.6f else 1f)) {
                SingleSelectRow(
                    title = "Not attending",
                    selected = sheet.notAttendingSelected,
                    onClick = { store.notAttending(sheet.lunchId) },
                    enabled = !busy,
                )
            }
            Text(
                "Your pick saves as soon as you tap it. You can change it until the deadline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            sheet.next?.let { next ->
                TextButton(onClick = { open(next.lunchId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(next.label)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        } else {
            // A closed day shows just what happened and what was on offer.
            PentFilledCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Text(sheet.closedSummary ?: "Ordering closed.", style = MaterialTheme.typography.bodyMedium)
                }
                sheet.menus.forEach { menu ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuHeading(menu.heading)
                }
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
    )
}
