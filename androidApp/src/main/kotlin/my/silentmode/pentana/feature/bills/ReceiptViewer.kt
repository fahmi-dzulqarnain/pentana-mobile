package my.silentmode.pentana.feature.bills

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import my.silentmode.pentana.shared.model.ReceiptPdf

/**
 * Writes [pdf] to cache/receipts (overwritten by name) and opens it in the member's PDF viewer through
 * the app's FileProvider. Returns false when no installed app can view PDFs.
 */
fun openReceiptPdf(context: Context, pdf: ReceiptPdf): Boolean {
    val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(folder, pdf.fileName).apply { writeBytes(pdf.bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
