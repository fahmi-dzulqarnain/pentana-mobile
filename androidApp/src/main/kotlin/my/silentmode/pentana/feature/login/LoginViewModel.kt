package my.silentmode.pentana.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.AuthRepository
import my.silentmode.pentana.shared.model.UserDto

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !loading

    fun onEmail(value: String) { email = value; error = null }
    fun onPassword(value: String) { password = value; error = null }

    fun submit(onSuccess: (UserDto) -> Unit) {
        if (!canSubmit) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val user = auth.login(email.trim(), password, "Android")
                loading = false
                onSuccess(user)
            } catch (_: Exception) {
                loading = false
                error = "The provided credentials are incorrect."
            }
        }
    }
}
