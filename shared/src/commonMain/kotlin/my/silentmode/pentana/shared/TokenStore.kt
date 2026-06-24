package my.silentmode.pentana.shared

/**
 * Persists the member's bearer token. The iOS app injects a Keychain-backed
 * implementation (written in Swift, conforming to this interface); Android injects
 * its own. Tests use [InMemoryTokenStore].
 */
interface TokenStore {
    suspend fun get(): String?

    suspend fun save(token: String)

    suspend fun clear()
}

/** Non-persistent default — handy for tests and previews. */
class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    override suspend fun get(): String? = token

    override suspend fun save(token: String) {
        this.token = token
    }

    override suspend fun clear() {
        token = null
    }
}
