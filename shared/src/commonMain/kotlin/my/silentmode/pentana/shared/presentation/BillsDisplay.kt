package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.PaymentProofDto

/** Shared decision - each platform maps this to its own chip/pill styling. */
enum class BillStatus { Paid, Partial, Overdue, Unpaid }

/** A payment proof's review state. Anything unrecognised reads as still being reviewed. */
enum class ProofStatus { Pending, Verified, Rejected }

fun proofStatus(proof: PaymentProofDto): ProofStatus = when {
    proof.status.equals("verified", ignoreCase = true) -> ProofStatus.Verified
    proof.status.equals("rejected", ignoreCase = true) -> ProofStatus.Rejected
    else -> ProofStatus.Pending
}

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
    // toDoubleOrNull accepts "NaN"/"Infinity" - numeric, but not valid currency amounts.
    return parsedAmount.isFinite() && hasPhoto
}
