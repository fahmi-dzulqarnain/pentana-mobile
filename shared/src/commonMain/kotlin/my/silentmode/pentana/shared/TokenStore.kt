package my.silentmode.pentana.shared

/**
 * Persists the member's bearer token. The iOS app injects a Keychain-backed
 * implementation (written in Swift, conforming to this interface); Android injects
 * its own. Tests use [InMemoryTokenStore].
 *
 * Synchronous on purpose: Keychain/Prefs access is fast + blocking, and a plain
 * (non-suspend) interface is trivial to implement from Swift.
 */
interface TokenStore {
    fun get(): String?

    fun save(token: String)

    fun clear()
}

/** Non-persistent default — handy for tests and previews. */
class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    override fun get(): String? = token

    override fun save(token: String) {
        this.token = token
    }

    override fun clear() {
        token = null
    }
}
