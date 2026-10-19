package my.silentmode.pentana

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.core.initials
import my.silentmode.pentana.feature.activities.ActivitiesScreen
import my.silentmode.pentana.feature.bills.BillsScreen
import my.silentmode.pentana.feature.home.HomeScreen
import my.silentmode.pentana.feature.login.LoginScreen
import my.silentmode.pentana.feature.lunch.LunchScreen
import my.silentmode.pentana.feature.notifications.NotificationsSheet
import my.silentmode.pentana.feature.profile.ProfileSheet
import my.silentmode.pentana.shared.model.UserDto
import my.silentmode.pentana.shared.model.avatarName
import my.silentmode.pentana.shared.model.greetingName
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.NavDest
import my.silentmode.pentana.ui.components.PentNavBar
import my.silentmode.pentana.ui.components.PentTopBar
import my.silentmode.pentana.ui.session.SessionViewModel
import my.silentmode.pentana.ui.theme.PentanaTheme
import my.silentmode.pentana.ui.update.UpdateDialog
import my.silentmode.pentana.ui.update.UpdateViewModel

@Composable
fun PentanaApp() {
    PentanaTheme {
        val session = appViewModel { SessionViewModel(it.sessionManager) }
        val sessionState by session.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { session.bootstrap() }

        when {
            sessionState.bootstrapping -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { LoadingState() }
            sessionState.user == null -> LoginScreen()
            else -> MainScaffold(session, sessionState.user!!, sessionState.unread)
        }

        // Also shown on the login screen: a build that cannot sign in still needs to reach the fix.
        val updates = appViewModel { UpdateViewModel(it.appUpdates, it.updateCheckSchedule, BuildConfig.VERSION_CODE) }
        val availableUpdate by updates.availableUpdate.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { updates.checkIfDue() }
        availableUpdate?.let { release -> UpdateDialog(release, onDismiss = updates::dismiss) }
    }
}

private fun NavDest.barTitle(): String? = if (this == NavDest.Home) null else label

@Composable
private fun MainScaffold(session: SessionViewModel, user: UserDto, unread: Int) {
    var active by rememberSaveable { mutableStateOf(NavDest.Home) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PentTopBar(
                title = active.barTitle(),
                unread = unread,
                avatarInitials = initials(user.avatarName),
                onAvatar = { showProfile = true },
                onBell = { showNotifications = true },
            )
        },
        bottomBar = { PentNavBar(active = active, onSelect = { active = it }) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (active) {
                NavDest.Home -> HomeScreen(userName = user.greetingName, onSwitchTab = { active = it })
                NavDest.Bills -> BillsScreen()
                NavDest.Lunch -> LunchScreen()
                NavDest.Activities -> ActivitiesScreen()
            }
        }
    }

    if (showProfile) {
        ProfileSheet(
            user = user,
            onSaveNickname = session::updateNickname,
            onSignOut = { showProfile = false; session.logout() },
            onDismiss = { showProfile = false },
        )
    }
    if (showNotifications) {
        NotificationsSheet(
            onMarkAllRead = session::markNotificationsRead,
            onDismiss = { showNotifications = false },
        )
    }
}
