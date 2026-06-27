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
import my.silentmode.pentana.feature.login.LoginScreen
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
    // Profile / Notifications / sheets are wired in later tasks.

    Scaffold(
        topBar = {
            PentTopBar(
                title = active.barTitle(),
                unread = unread,
                avatarInitials = initials(user.name),
                onAvatar = { /* Profile sheet — Task 11 */ },
                onBell = { session.markNotificationsRead() /* Notifications sheet — Task 11 */ },
            )
        },
        bottomBar = { PentNavBar(active = active, onSelect = { active = it }) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            when (active) {
                NavDest.Home -> TabPlaceholder("Home")
                NavDest.Bills -> TabPlaceholder("Bills")
                NavDest.Lunch -> TabPlaceholder("Lunch")
                NavDest.Activities -> TabPlaceholder("Activities")
            }
        }
    }
}

@Composable
private fun TabPlaceholder(name: String) {
    Text("$name — coming up", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
