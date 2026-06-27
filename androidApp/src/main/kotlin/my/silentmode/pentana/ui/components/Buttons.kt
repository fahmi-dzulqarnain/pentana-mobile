package my.silentmode.pentana.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BtnVariant { Filled, Tonal, Outlined, Text, Destructive }

@Composable
fun PentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BtnVariant = BtnVariant.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val body: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    val on = enabled && !loading
    when (variant) {
        BtnVariant.Filled -> Button(onClick, modifier.height(48.dp), enabled = on, shape = shape) { body() }
        BtnVariant.Tonal -> FilledTonalButton(onClick, modifier.height(48.dp), enabled = on, shape = shape) { body() }
        BtnVariant.Outlined -> OutlinedButton(onClick, modifier.height(48.dp), enabled = on, shape = shape) { body() }
        BtnVariant.Text -> TextButton(onClick, modifier, enabled = on) { body() }
        BtnVariant.Destructive -> TextButton(
            onClick, modifier, enabled = on,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { body() }
    }
}

/** Extended FAB for the Bills "Submit proof" action. */
@Composable
fun SubmitFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = { Icon(Icons.Filled.Upload, null) },
        text = { Text("Submit proof") },
    )
}
