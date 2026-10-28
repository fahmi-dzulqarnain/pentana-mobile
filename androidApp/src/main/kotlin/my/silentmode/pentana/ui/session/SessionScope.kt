package my.silentmode.pentana.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import my.silentmode.pentana.shared.model.UserDto

/** The signed-out screens share one scope; each signed-in member gets their own. */
fun sessionScopeKey(user: UserDto?): String = user?.let { "user:${it.id}" } ?: "signed-out"

/**
 * One [ViewModelStore] for the current session key. Asking for a different key clears the
 * previous store first, so no ViewModel (and none of the member data or typed password it
 * holds) outlives a sign-out or an account switch. Asking again for the same key (e.g. after
 * a rotation) returns the same store.
 */
class SessionViewModelStores {
    private var currentKey: String? = null
    private var current: ViewModelStore? = null

    fun storeFor(key: String): ViewModelStore {
        current?.let { if (key == currentKey) return it }
        current?.clear()
        return ViewModelStore().also { current = it; currentKey = key }
    }

    fun clear() {
        current?.clear()
        current = null
        currentKey = null
    }
}

/** Activity-scoped so the session's store survives configuration changes. */
class SessionStoresHolder : ViewModel() {
    val stores = SessionViewModelStores()

    override fun onCleared() = stores.clear()
}

/**
 * Gives [content] a ViewModelStoreOwner scoped to [scopeKey] (see [sessionScopeKey]):
 * every `appViewModel {}` inside lives and dies with that session, not with the Activity.
 */
@Composable
fun SessionScope(scopeKey: String, content: @Composable () -> Unit) {
    val holder = viewModel<SessionStoresHolder>()
    key(scopeKey) {
        val owner = remember {
            val store = holder.stores.storeFor(scopeKey)
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
    }
}
