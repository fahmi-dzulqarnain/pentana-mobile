package my.silentmode.pentana.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import my.silentmode.pentana.shared.TokenStore

/** Bearer-token store backed by EncryptedSharedPreferences (encrypted at rest, like the iOS Keychain). */
class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "pentana_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(): String? = prefs.getString(KEY, null)
    override fun save(token: String) { prefs.edit().putString(KEY, token).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }

    private companion object {
        const val KEY = "auth_token"
    }
}
