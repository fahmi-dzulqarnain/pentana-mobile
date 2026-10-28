package my.silentmode.pentana.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.todayLong
import my.silentmode.pentana.feature.bills.BillsViewModel
import my.silentmode.pentana.feature.bills.SubmitProofSheet
import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto
import my.silentmode.pentana.shared.presentation.DashboardActivityStatus
import my.silentmode.pentana.shared.presentation.HomeSections
import my.silentmode.pentana.shared.presentation.HomeTodo
import my.silentmode.pentana.shared.presentation.HomeUiState
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.dashboardActivityStatus
import my.silentmode.pentana.shared.presentation.dashboardLunchStatus
import my.silentmode.pentana.shared.presentation.homeSections
import my.silentmode.pentana.shared.presentation.supportCardSubtitle
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LeadingIcon
import my.silentmode.pentana.ui.components.NavDest
import my.silentmode.pentana.ui.theme.LocalPentanaColors

/** The most to-dos shown before "+N more". */
private const val TODO_CAP = 3

/**
 * Home: date + greeting, "Needs you" (the member's to-dos, most urgent first, capped at 3) or the
 * all-clear card, then "Coming up". The shared [homeSections] decides what shows, so Android and
 * iOS never disagree on which to-dos appear or in what order.
 *
 * [onOpenSupport] opens Ask the committee: a ticket id opens that thread, null the ticket list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(userName: String, isAdmin: Boolean, onSwitchTab: (NavDest) -> Unit, onOpenSupport: (Long?) -> Unit) {
    val vm = appViewModel { HomeViewModel(it.dashboard) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    var showSubmitProof by rememberSaveable { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val uiState = state) {
            is HomeUiState.Loading -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Greeting(userName)
                HomeSkeleton()
            }
            is HomeUiState.Error -> ErrorState(uiState.message, vm.store::load)
            is HomeUiState.Content -> HomeContent(
                userName = userName,
                dashboard = uiState.data,
                isAdmin = isAdmin,
                onSwitchTab = onSwitchTab,
                onOpenSupport = onOpenSupport,
                onResubmitProof = { showSubmitProof = true },
            )
        }
    }

    if (showSubmitProof) {
        // The same session-scoped Bills store BillsScreen uses (appViewModel keys by class), so a
        // resubmitted proof also shows on Bills. Only created once the sheet opens.
        val bills = appViewModel { BillsViewModel(it.bills) }
        SubmitProofSheet(
            store = bills.store,
            onDismiss = {
                showSubmitProof = false
                bills.store.resetSubmit()
                vm.refresh()
            },
        )
    }
}

@Composable
private fun HomeContent(
    userName: String,
    dashboard: DashboardDto,
    isAdmin: Boolean,
    onSwitchTab: (NavDest) -> Unit,
    onOpenSupport: (Long?) -> Unit,
    onResubmitProof: () -> Unit,
) {
    val sections = remember(dashboard) { homeSections(dashboard) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
    ) {
        Greeting(userName)
        if (sections.todos.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            AllClearCard()
        } else {
            NeedsYou(sections.todos) { todo ->
                when (todo) {
                    is HomeTodo.RejectedProof -> onResubmitProof()
                    is HomeTodo.Dues -> onSwitchTab(NavDest.Bills)
                    is HomeTodo.LunchVote -> onSwitchTab(NavDest.Lunch)
                    // One waiting ticket opens its thread; two or more open the list.
                    is HomeTodo.SupportReply -> onOpenSupport(if (todo.count == 1) todo.ticketId else null)
                }
            }
        }
        ComingUp(sections, onSwitchTab)
        // Starting a new question; admins answer on the web, so members only.
        if (!isAdmin) {
            Spacer(Modifier.height(14.dp))
            val colors = LocalPentanaColors.current
            HomeCard {
                ComingUpRow(
                    title = "Ask the committee",
                    onClick = { onOpenSupport(null) },
                    leading = { HomeIcon(Icons.Rounded.SupportAgent, colors.lunch.color, colors.lunch.container) },
                    detail = { DetailText(supportCardSubtitle(0)) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Greeting
// ---------------------------------------------------------------------------------------------

@Composable
private fun Greeting(userName: String) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp)) {
        Text(todayLong(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Hi, ${userName.ifBlank { "there" }}",
            modifier = Modifier.semantics { heading() },
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Needs you
// ---------------------------------------------------------------------------------------------

@Composable
private fun NeedsYou(todos: List<HomeTodo>, onOpen: (HomeTodo) -> Unit) {
    // "+N more" expands in place; a changed to-do list (after a refresh) collapses it again.
    var showAll by remember(todos) { mutableStateOf(false) }
    val colors = LocalPentanaColors.current
    val visible = if (showAll) todos else todos.take(TODO_CAP)
    SectionHeader("Needs you", count = todos.size)
    HomeCard {
        visible.forEachIndexed { index, todo ->
            if (index > 0) HomeRowDivider()
            TodoRow(todoStyle(todo, colors), todoText(todo), onClick = { onOpen(todo) })
        }
        if (!showAll && todos.size > TODO_CAP) {
            HomeRowDivider()
            MoreRow(hidden = todos.size - TODO_CAP, nextTitle = todoText(todos[TODO_CAP]).title) { showAll = true }
        }
    }
}

@Composable
private fun MoreRow(hidden: Int, nextTitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClickLabel = "Show the rest of the list", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "+$hidden more · $nextTitle",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HomeChevron()
    }
}

// ---------------------------------------------------------------------------------------------
// All clear
// ---------------------------------------------------------------------------------------------

@Composable
private fun AllClearCard() {
    val colors = LocalPentanaColors.current
    HomeCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            LeadingIcon(Icons.Rounded.Celebration, colors.ok.container, colors.ok.color, size = 46.dp, iconSize = 24.dp, radius = 23.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("You're all clear", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("No dues, no votes waiting. Nice.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Coming up
// ---------------------------------------------------------------------------------------------

@Composable
private fun ComingUp(sections: HomeSections, onSwitchTab: (NavDest) -> Unit) {
    val lunch = sections.comingUpLunch
    val activity = sections.comingUpActivity
    val proofInReview = sections.proofInReview
    val openCount = sections.openActivitiesCount
    val hasRows = proofInReview != null || lunch != null || activity != null

    SectionHeader("Coming up")
    if (!hasRows && openCount == 0) {
        EmptyComingUp { onSwitchTab(NavDest.Activities) }
        return
    }
    HomeCard {
        if (proofInReview != null) {
            ProofInReviewRow(proofInReview) { onSwitchTab(NavDest.Bills) }
        }
        if (lunch != null) {
            if (proofInReview != null) HomeRowDivider()
            LunchRow(lunch) { onSwitchTab(NavDest.Lunch) }
        }
        if (activity != null) {
            if (proofInReview != null || lunch != null) HomeRowDivider()
            ActivityRow(activity) { onSwitchTab(NavDest.Activities) }
        }
        if (openCount > 0) {
            if (hasRows) HomeRowDivider()
            OpenToJoinRow(openCount) { onSwitchTab(NavDest.Activities) }
        }
    }
}

@Composable
private fun ProofInReviewRow(amount: String, onClick: () -> Unit) {
    val colors = LocalPentanaColors.current
    ComingUpRow(
        title = "Proof in review · MYR $amount",
        onClick = onClick,
        leading = { HomeIcon(Icons.Rounded.Schedule, colors.proof.color, colors.proof.container) },
        detail = { DetailText("Dues clear once the committee approves") },
    )
}

@Composable
private fun LunchRow(lunch: DashboardLunchDto, onClick: () -> Unit) {
    val colors = LocalPentanaColors.current
    val date = ymd(lunch.date)
    ComingUpRow(
        title = lunchLabel(lunch),
        onClick = onClick,
        leading = {
            if (date != null) {
                DateChip(weekday(date), date.dayOfMonth.toString(), DateChipKind.Lunch)
            } else {
                HomeIcon(Icons.Rounded.CalendarMonth, colors.lunch.color, colors.lunch.container)
            }
        },
        detail = {
            when (dashboardLunchStatus(lunch)) {
                LunchStatus.Responded -> HomePill("Voted", colors.okStrong, colors.ok.container)
                LunchStatus.Closed -> HomePill(
                    "Closed",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                // An open, unvoted next lunch is a to-do; this only shows for an open other_lunch.
                LunchStatus.VoteNow -> HomePill("Vote now", colors.warn.color, colors.warn.container)
            }
        },
    )
}

@Composable
private fun ActivityRow(activity: DashboardActivityDto, onClick: () -> Unit) {
    val colors = LocalPentanaColors.current
    val start = activity.startsAt?.let { zoned(it) }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    ComingUpRow(
        title = activity.title,
        onClick = onClick,
        leading = {
            if (start != null) {
                DateChip(weekday(start.toLocalDate()), start.dayOfMonth.toString(), DateChipKind.Activity)
            } else {
                HomeIcon(Icons.Rounded.CalendarMonth, colors.activ.color, colors.activ.container)
            }
        },
        detail = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // next_activity is always one of the member's registrations, so anything
                // non-waitlisted renders as Registered.
                if (dashboardActivityStatus(activity) == DashboardActivityStatus.Waitlisted) {
                    HomePill("Waitlisted", colors.warn.color, colors.warn.container)
                } else {
                    HomePill("Registered", colors.okStrong, colors.ok.container)
                }
                if (start != null) DetailText(timeOfDay(start, is24Hour))
            }
        },
    )
}

@Composable
private fun OpenToJoinRow(count: Int, onClick: () -> Unit) {
    val color = LocalPentanaColors.current.okStrong
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            if (count == 1) "1 activity open to join" else "$count activities open to join",
            modifier = Modifier.weight(1f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HomeChevron()
    }
}

@Composable
private fun EmptyComingUp(onBrowse: () -> Unit) {
    val cue = LocalPentanaColors.current.cue
    HomeCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Nothing coming up", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "No lunches or activities you've joined yet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onBrowse,
                colors = ButtonDefaults.buttonColors(containerColor = cue.container, contentColor = cue.color),
            ) {
                Text("Browse activities", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
