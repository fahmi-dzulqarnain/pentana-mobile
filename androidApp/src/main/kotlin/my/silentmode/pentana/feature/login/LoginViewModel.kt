package my.silentmode.pentana.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.presentation.SessionManager

/** Form state stays here (screen-local); the actual login + error copy live in the shared manager. */
class LoginViewModel(private val sessionManager: SessionManager) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set

    val loginError = sessionManager.loginError

    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !loading

    fun onEmail(value: String) { email = value; sessionManager.dismissLoginError() }
    fun onPassword(value: String) { password = value; sessionManager.dismissLoginError() }

    fun submit() {
        if (!canSubmit) return
        loading = true
        viewModelScope.launch {
            sessionManager.login(email, password, "Android")
            loading = false
        }
    }
}
