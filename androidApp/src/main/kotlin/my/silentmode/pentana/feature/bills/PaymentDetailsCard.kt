package my.silentmode.pentana.feature.bills

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.PaymentDetailsDto
import my.silentmode.pentana.shared.presentation.BillsStore
import my.silentmode.pentana.ui.components.BtnVariant
import my.silentmode.pentana.ui.components.PentButton
import my.silentmode.pentana.ui.components.PentFilledCard

private const val QR_FILE_NAME = "kelab-pentana-duitnow-qr.jpg"

/**
 * Where to transfer fees: bank, account number and the member's reference (each copyable), plus the
 * club's DuitNow QR. A banking app can't scan a QR on its own screen, so the QR opens full size with
 * Save (to the gallery, for the bank app's "scan from photo") and Share.
 */
@Composable
fun PaymentDetailsCard(
    details: PaymentDetailsDto,
    store: BillsStore,
    modifier: Modifier = Modifier,
    /** The proof sheet shows only the account rows; the Bills tab shows the QR too. */
    showQr: Boolean = true,
) {
    var qr by remember(details.qrUrl) { mutableStateOf<Pair<ByteArray, ImageBitmap>?>(null) }
    var qrOpen by remember { mutableStateOf(false) }

    if (showQr) {
        LaunchedEffect(details.qrUrl) {
            val bytes = store.paymentQr(details.qrUrl) ?: return@LaunchedEffect
            val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            if (bitmap != null) qr = bytes to bitmap.asImageBitmap()
        }
    }

    PentFilledCard(modifier.fillMaxWidth()) {
        DetailRow("Bank", details.bankName)
        RowDivider()
        DetailRow("Account number", details.accountNumber, copyable = true)
        RowDivider()
        DetailRow("Account name", details.accountName)
        RowDivider()
        DetailRow("Reference", details.reference, copyable = true)
        qr?.let { (_, image) ->
            RowDivider()
            Row(
                Modifier.fillMaxWidth().clickable { qrOpen = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    image, contentDescription = null,
                    modifier = Modifier.width(44.dp).height(62.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f)) {
                    Text("DuitNow QR", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text("Tap to view or save", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val opened = qr
    if (qrOpen && opened != null) {
        QrDialog(opened.first, opened.second, details.accountName, onDismiss = { qrOpen = false })
    }
}

@Composable
private fun DetailRow(label: String, value: String, copyable: Boolean = false) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        }
        if (copyable) {
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                copied = true
            }) {
                Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (copied) "Copied" else "Copy", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 18.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun QrDialog(bytes: ByteArray, image: ImageBitmap, accountName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    image, contentDescription = "DuitNow QR for $accountName",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.FillWidth,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // MediaStore needs no permission from Android 10; older phones share it to their gallery instead.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PentButton(
                            text = "Save",
                            onClick = {
                                val saved = saveQrToGallery(context, bytes)
                                Toast.makeText(context, if (saved) "Saved to Photos" else "Couldn't save the QR.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Filled.Download,
                        )
                    }
                    PentButton(
                        text = "Share",
                        onClick = { if (!shareQr(context, bytes)) Toast.makeText(context, "Couldn't share the QR.", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.weight(1f),
                        variant = BtnVariant.Tonal,
                        leadingIcon = Icons.Filled.Share,
                    )
                }
            }
        }
    }
}

/** Adds the QR to Pictures/PENTANA through MediaStore (Android 10+, no storage permission). */
private fun saveQrToGallery(context: Context, bytes: ByteArray): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, QR_FILE_NAME)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PENTANA")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("No output stream")
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

/** Shares the QR through the app's FileProvider (cache/qr, declared in res/xml/file_paths.xml). */
private fun shareQr(context: Context, bytes: ByteArray): Boolean {
    val file = try {
        File(File(context.cacheDir, "qr").apply { mkdirs() }, QR_FILE_NAME).apply { writeBytes(bytes) }
    } catch (_: IOException) {
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "DuitNow QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}
