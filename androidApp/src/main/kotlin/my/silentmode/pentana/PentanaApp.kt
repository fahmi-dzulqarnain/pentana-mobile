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
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.components.NavDest
import my.silentmode.pentana.ui.components.PentNavBar
import my.silentmode.pentana.ui.components.PentTopBar
import my.silentmode.pentana.ui.session.SessionViewModel
import my.silentmode.pentana.ui.theme.PentanaTheme

@Composable
fun PentanaApp() {
    PentanaTheme {
        val session = appViewModel { SessionViewModel(it.auth, it.notifications) }
        val s by session.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { session.bootstrap() }

        when {
            s.bootstrapping -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { LoadingState() }
            s.user == null -> LoginScreen(onSignedIn = session::onLoggedIn)
            else -> MainScaffold(session, s.user!!, s.unread)
        }
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
                avatarInitials = initials(user.name),
                onAvatar = { showProfile = true },
                onBell = { showNotifications = true },
            )
        },
        bottomBar = { PentNavBar(active = active, onSelect = { active = it }) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (active) {
                NavDest.Home -> HomeScreen(userName = user.name, onSwitchTab = { active = it })
                NavDest.Bills -> BillsScreen()
                NavDest.Lunch -> LunchScreen()
                NavDest.Activities -> ActivitiesScreen()
            }
        }
    }

    if (showProfile) {
        ProfileSheet(
            user = user,
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
