package my.silentmode.pentana.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import my.silentmode.pentana.App
import my.silentmode.pentana.core.AppContainer

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as App).container

/** Builds a ViewModel from the app's manual-DI container, mirroring the iOS SessionStore wiring. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
