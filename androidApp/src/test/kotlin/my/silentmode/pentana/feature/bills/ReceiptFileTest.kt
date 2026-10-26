package my.silentmode.pentana.feature.bills

import java.io.File
import java.nio.file.Files
import my.silentmode.pentana.shared.model.ReceiptPdf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptFileTest {
    private val pdf = ReceiptPdf("PEN-2026-0001.pdf", "%PDF-1.7".encodeToByteArray())

    @Test fun writes_the_pdf_into_a_receipts_folder() {
        val cacheFolder = Files.createTempDirectory("cache").toFile()
        val written = writeReceiptFile(cacheFolder, pdf)
        assertEquals(File(cacheFolder, "receipts/PEN-2026-0001.pdf"), written)
        assertContentEquals(pdf.bytes, written?.readBytes())
    }

    @Test fun unwritable_cache_returns_null_instead_of_throwing() {
        // A plain file where the receipts folder should be: mkdirs fails, writeBytes would throw.
        val cacheFolder = Files.createTempDirectory("cache").toFile()
        File(cacheFolder, "receipts").writeText("not a folder")
        assertNull(writeReceiptFile(cacheFolder, pdf))
    }
}
