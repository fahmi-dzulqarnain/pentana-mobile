package my.silentmode.pentana.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import my.silentmode.pentana.shared.model.AndroidReleaseDto
import java.util.Locale

/** Offers the newer build; "Update" downloads it in the browser, where Android installs it over this one. */
@Composable
fun UpdateDialog(release: AndroidReleaseDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sizeInMegabytes = String.format(Locale.US, "%.1f", release.size / 1_048_576.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PENTANA ${release.versionName} is ready to download ($sizeInMegabytes MB).")
                release.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)))
                onDismiss()
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}
