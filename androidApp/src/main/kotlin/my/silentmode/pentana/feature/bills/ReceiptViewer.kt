package my.silentmode.pentana.feature.bills

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import my.silentmode.pentana.shared.model.ReceiptPdf

enum class ReceiptOpenResult { Opened, NoViewer, WriteFailed }

/**
 * Writes [pdf] to cache/receipts and opens it in the member's PDF viewer through the app's
 * FileProvider (authority and path declared in the manifest and res/xml/file_paths.xml).
 */
fun openReceiptPdf(context: Context, pdf: ReceiptPdf): ReceiptOpenResult {
    val file = writeReceiptFile(context.cacheDir, pdf) ?: return ReceiptOpenResult.WriteFailed
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        ReceiptOpenResult.Opened
    } catch (_: ActivityNotFoundException) {
        ReceiptOpenResult.NoViewer
    }
}

/** Writes [pdf] to [cacheFolder]/receipts, overwriting by name. Null when the cache can't be written. */
fun writeReceiptFile(cacheFolder: File, pdf: ReceiptPdf): File? = try {
    val folder = File(cacheFolder, "receipts").apply { mkdirs() }
    File(folder, pdf.fileName).apply { writeBytes(pdf.bytes) }
} catch (_: IOException) {
    null
}
