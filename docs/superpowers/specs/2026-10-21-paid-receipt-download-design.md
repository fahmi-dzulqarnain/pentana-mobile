# Paid receipt download — design

## Why

The web app (pentana-system PR #15, branch `feat/paid-receipt`) now issues a numbered PDF receipt
(`PEN-YYYY-NNNN`) when an admin verifies a member's payment proof. Members should be able to open
that receipt from the mobile apps, on the payment proof it belongs to, without going to the web.

## Goals

- A **View receipt** button on each verified payment proof that has a receipt, on iOS and Android.
- Tapping it downloads the PDF with the member's bearer token and opens it in the system viewer:
  QuickLook on iOS (its toolbar already offers Share / Save to Files / Print); `ACTION_VIEW`
  through a `FileProvider` URI on Android.
- 404 (not the owner, no receipt, voided) and network failures show one short message.

## Non-goals

- No receipt list/history screen, no caching or offline storage of receipts beyond the temp file
  needed to hand the PDF to the viewer.
- No receipt for proofs verified before launch (`has_receipt` false): no button, no explanation.
- No localisation (receipts are always English; the app is English-only).

## API (from the server PR)

- `GET /api/v1/payment-proofs` items gain `receipt_number` (string|null) and `has_receipt` (bool).
- `GET /api/v1/payment-proofs/{id}/receipt` → `application/pdf`,
  `Content-Disposition: attachment; filename="PEN-2026-0001.pdf"`; 404 when not the owner or no
  receipt. Same bearer token.

## Decided with Fahmi (2026-10-21)

1. **Android gets the payment-proofs list first.** On `main`, only iOS lists proofs on Bills
   (commit 9d56acd); Android has nowhere to host the button. Port the iOS "Payment proofs"
   section to Android: amount, submitted time, status chip (In review / Verified / Rejected),
   "Verified as MYR x" when it differs, rejection reason, "Submit a new proof" (opens the existing
   submit sheet). Placed between the summary card and "Bill history", hidden when empty — same as iOS.
2. **iOS opens QuickLook**, not the share sheet directly.
3. **Label:** a small text button "View receipt" in the same style as "Submit a new proof", with the
   receipt number (`PEN-2026-0001`) shown as a caption beside/below it.

## Shared (KMP)

- `PaymentProofDto`: `receiptNumber: String? = null` (`receipt_number`), `hasReceipt: Boolean = false`
  (`has_receipt`). Defaults keep older servers (current production) decoding — no button there.
- `ReceiptPdf(fileName: String, bytes: ByteArray)` model.
- `BillsRepository.receipt(proofId: Long): ReceiptPdf` — authed GET with `Accept: application/pdf`,
  `ensureSuccess`, file name from `Content-Disposition`, sanitised to a safe basename ending `.pdf`;
  fallback `receipt-<id>.pdf`.
- `BillsStore.receipt: StateFlow<ReceiptState>` with `Idle`, `Downloading(proofId)`,
  `Ready(proofId, pdf)`, `Error(message)`.
  - `openReceipt(proofId)`: ignored while any download is in flight (one at a time — the viewer is
    modal anyway). Sets `Downloading`, then `Ready` or `Error`.
  - 404 → "This receipt isn't available." Anything else → "Couldn't download the receipt. Please try again."
  - `consumeReceipt()` → `Idle`, called by the platform once it has handed the PDF to the viewer, or
    when the error message is dismissed.
- iOS helper `dataFrom(bytes: ByteArray): NSData` next to the existing `byteArrayFrom` (memcpy, not
  per-element copy).
- Tests (commonTest, MockEngine): DTO decodes the new fields and defaults when absent; repository
  sends the bearer token to `/payment-proofs/{id}/receipt` and reads the file name; store goes
  Downloading → Ready; 404 and network error map to their messages; a second `openReceipt` while
  downloading fires no request; `consumeReceipt` returns to Idle.

## iOS

- `ProofRow` gains the button for `status == .verified && proof.hasReceipt`; shows a small spinner in
  place of the button text while that proof downloads, disabled while any download runs.
- `BillsView` observes `store.receipt`. On `Ready`: write bytes to
  `FileManager.temporaryDirectory/receipts/<fileName>`, present a QuickLook preview
  (`QLPreviewController` wrapped for SwiftUI, or `.quickLookPreview($url)`), call `consumeReceipt()`.
  On `Error`: an alert with the message; dismissing it calls `consumeReceipt()` (binding setter clears
  local state synchronously — M1 precedent).

## Android

- New proofs section + `ProofRow` in `feature/bills/BillsScreen.kt`, reusing existing `PentListItem`,
  `StatusChip` (adding proof chip kinds if missing), `SectionHeader`, `PentFilledCard`.
- `FileProvider` (`${applicationId}.fileprovider`) declared in the manifest with
  `res/xml/file_paths.xml` exposing `cache/receipts/`.
- On `Ready`: write to `cacheDir/receipts/<fileName>`, `ACTION_VIEW` with `application/pdf` and
  `FLAG_GRANT_READ_URI_PERMISSION`; `ActivityNotFoundException` → snackbar/toast "No app can open PDFs".
  Then `consumeReceipt()`. On `Error`: short snackbar/toast with the message, then `consumeReceipt()`.

## Verification

- `:shared:allTests` (or the jvm/ios test tasks the repo uses), `:androidApp:testDebugUnitTest`,
  `:androidApp:assembleDebug`, iOS simulator build.
- Manual run against a local pentana-system on `feat/paid-receipt` (never its `.env` production
  database): iOS simulator → `localhost`, Android emulator → `10.0.2.2`. Check: verified proof with
  receipt shows button and opens the PDF; pre-launch verified proof shows no button; 404 path.

## Builder's calls (not discussed with Fahmi)

- A `Ready`/`Error` that arrives after the member has left Bills is dropped: iOS calls
  `consumeReceipt()` on disappear, Android handles `Ready` once in a `LaunchedEffect` and consumes it
  immediately, so rotation doesn't reopen the viewer.
- 401 (expired session) gets the generic download message, like any non-404 failure.

## Left to the builder

Exact spacing/iconography, whether the Android message is a snackbar or toast, temp-file cleanup
policy (overwrite by name is fine).
