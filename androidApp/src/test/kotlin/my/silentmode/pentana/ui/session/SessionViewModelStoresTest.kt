package my.silentmode.pentana.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import my.silentmode.pentana.shared.model.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionViewModelStoresTest {
    private class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    private fun probeIn(store: ViewModelStore): Probe =
        ViewModelProvider.create(store, viewModelFactory { initializer { Probe() } })[Probe::class]

    private fun user(id: Long) = UserDto(id = id, name = "M$id", email = "m$id@example.com")

    @Test fun keys_per_member_and_one_for_signed_out() {
        assertEquals("user:1", sessionScopeKey(user(1)))
        assertEquals("user:2", sessionScopeKey(user(2)))
        assertEquals("signed-out", sessionScopeKey(null))
    }

    @Test fun same_key_keeps_the_store_across_recreation() {
        val stores = SessionViewModelStores()
        val first = stores.storeFor("user:1")
        val probe = probeIn(first)

        assertSame(first, stores.storeFor("user:1"))
        assertFalse(probe.cleared)
    }

    @Test fun sign_out_then_another_member_clears_every_previous_view_model() {
        val stores = SessionViewModelStores()
        val memberA = probeIn(stores.storeFor(sessionScopeKey(user(1))))

        val signedOut = stores.storeFor(sessionScopeKey(null))
        assertTrue(memberA.cleared)
        val login = probeIn(signedOut)

        val memberB = stores.storeFor(sessionScopeKey(user(2)))
        assertTrue(login.cleared)
        assertNotSame(memberA, probeIn(memberB))
    }

    @Test fun same_member_signing_back_in_gets_a_fresh_store() {
        val stores = SessionViewModelStores()
        val before = stores.storeFor("user:1")
        stores.storeFor("signed-out")
        assertNotSame(before, stores.storeFor("user:1"))
    }
}
