package my.silentmode.pentana.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.R
import my.silentmode.pentana.ui.appViewModel
import my.silentmode.pentana.ui.components.BtnVariant
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.PentTextField

@Composable
fun LoginScreen() {
    val vm = appViewModel { LoginViewModel(it.sessionManager) }
    val loginError by vm.loginError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(loginError) { loginError?.let { message -> snackbar.showSnackbar(message) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 28.dp)
                .imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.pentana_mark),
                contentDescription = null,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Text("PENTANA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em)
            Text("Member sign in", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))

            PentTextField(
                value = vm.email,
                onValueChange = vm::onEmail,
                label = "Email",
                leadingIcon = Icons.Filled.MailOutline,
                placeholder = "you@org.my",
                isError = loginError != null,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(14.dp))
            PentTextField(
                value = vm.password,
                onValueChange = vm::onPassword,
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                placeholder = "Your password",
                isError = loginError != null,
                supportingText = loginError,
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(6.dp))
            PentButton(
                text = "Sign in",
                onClick = vm::submit,
                modifier = Modifier.fillMaxWidth(),
                variant = BtnVariant.Filled,
                enabled = vm.canSubmit,
                loading = vm.loading,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Need access? Claim your account on the web",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
