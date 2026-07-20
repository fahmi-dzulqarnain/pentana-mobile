package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.BillDto

/** Shared decision — each platform maps this to its own chip/pill styling. */
enum class BillStatus { Paid, Partial, Overdue, Unpaid }

fun billStatus(bill: BillDto): BillStatus = when {
    bill.status.equals("paid", ignoreCase = true) -> BillStatus.Paid
    bill.status.equals("partial", ignoreCase = true) -> BillStatus.Partial
    bill.status.equals("overdue", ignoreCase = true) -> BillStatus.Overdue
    else -> BillStatus.Unpaid
}

/**
 * Submit is allowed once the amount parses as a number and a photo is attached.
 * Unified on iOS's stricter numeric check (Android previously allowed any non-blank string).
 */
fun canSubmitProof(amount: String, hasPhoto: Boolean): Boolean {
    val parsedAmount = amount.trim().toDoubleOrNull() ?: return false
    // toDoubleOrNull accepts "NaN"/"Infinity" — numeric, but not valid currency amounts.
    return parsedAmount.isFinite() && hasPhoto
}
