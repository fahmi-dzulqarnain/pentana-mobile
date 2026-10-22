# Paid Receipt Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "View receipt" button on verified payment proofs that downloads the server's PDF receipt and opens it in the system viewer, on iOS and Android.

**Architecture:** Shared `BillsRepository.receipt()` fetches the PDF bytes; `BillsStore` exposes a one-shot `ReceiptState` flow that each platform consumes (write to cache, open viewer, `consumeReceipt()`). Android first gains the payment-proofs list iOS already has.

**Tech Stack:** KMP + Ktor (MockEngine tests), SKIE, SwiftUI + QuickLook, Jetpack Compose + FileProvider.

**Spec:** `docs/superpowers/specs/2026-10-21-paid-receipt-download-design.md`

## Global Constraints

- Descriptive names only — no single-letter or abbreviated locals/params/bindings (Fahmi's rule; overrides house style).
- Copy: button "View receipt"; caption = `receiptNumber`; 404 → "This receipt isn't available."; other failure → "Couldn't download the receipt. Please try again."; Android no-viewer → "No app can open PDFs."
- Button shows only when `proofStatus(proof) == Verified && proof.hasReceipt`.
- Never point pentana-system at its `.env` database (production Supabase) — local verification uses a scratch SQLite DB via env overrides.

## Review Focus

- Server header missing/odd `Content-Disposition` (no filename, path chars like `../x.pdf`) → saved as a safe basename ending `.pdf`, fallback `receipt-<id>.pdf`. Test in Task 1.
- Older server without the new fields → decodes, `hasReceipt == false`, no button. Test in Task 1.
- Double tap / tap on a second receipt while one downloads → only one request. Test in Task 2.
- Receipt result arriving after leaving Bills → not presented later (iOS consume on disappear; Android consumes inside the effect). Manual check in Tasks 3/4.
- A 401 (expired token) → generic message, not a crash. Test in Task 2.

---

### Task 1: DTO fields + repository download

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/model/Dtos.kt` (PaymentProofDto; add `ReceiptPdf`)
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/BillsRepository.kt`
- Modify: `shared/src/iosMain/kotlin/my/silentmode/pentana/shared/DataConversion.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/BillsStoreTest.kt` (or new `BillsRepositoryTest.kt`)

**Interfaces — Produces:**
- `PaymentProofDto.receiptNumber: String? = null` (`receipt_number`), `PaymentProofDto.hasReceipt: Boolean = false` (`has_receipt`)
- `class ReceiptPdf(val fileName: String, val bytes: ByteArray)` (plain class, not data)
- `suspend fun BillsRepository.receipt(proofId: Long): ReceiptPdf` (`@Throws(Exception::class)`), throws `ApiException` on non-2xx
- `internal fun receiptFileName(contentDisposition: String?, proofId: Long): String`
- iOS: `fun dataFrom(bytes: ByteArray): NSData`

- [x] Tests: `proof_decodes_receipt_fields` (`"receipt_number":"PEN-2026-0001","has_receipt":true`), `proof_without_receipt_fields_defaults_to_no_receipt`, `receipt_download_sends_token_and_reads_file_name` (asserts path `/api/v1/payment-proofs/11/receipt`, `Authorization: Bearer t`, `fileName == "PEN-2026-0001.pdf"`, bytes equal), `receipt_404_throws_api_exception` (status 404), `receipt_file_name_is_sanitised` (`null`→`receipt-11.pdf`, `attachment; filename="../evil.pdf"`→`evil.pdf`, `attachment; filename="PEN-2026-0001"`→`PEN-2026-0001.pdf`).
- [x] Run `./gradlew :shared:testDebugUnitTest` → FAIL (unresolved).
- [x] Implement. File name: extract `filename="..."` (quoted or bare), take substring after last `/` or `\`, keep `[A-Za-z0-9._-]`, ensure `.pdf` suffix, blank → fallback. `dataFrom` uses `usePinned` + `NSData.create(bytes:length:)`.
- [x] Run → PASS. Commit `feat(shared): receipt fields and PDF download on BillsRepository`.

### Task 2: BillsStore receipt state

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsStore.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/BillsStoreTest.kt`

**Interfaces — Consumes:** `BillsRepository.receipt`, `ReceiptPdf`, `ApiException.status`.
**Produces:**
```kotlin
sealed interface ReceiptState {
    data object Idle : ReceiptState
    data class Downloading(val proofId: Long) : ReceiptState
    class Ready(val proofId: Long, val pdf: ReceiptPdf) : ReceiptState
    data class Error(val message: String) : ReceiptState
}
val BillsStore.receipt: StateFlow<ReceiptState>
fun BillsStore.openReceipt(proofId: Long)   // no-op while Downloading
fun BillsStore.consumeReceipt()             // -> Idle
```

- [x] Tests: `open_receipt_downloads_then_ready` (Downloading(11) observed via a parked `CompletableDeferred`, then Ready with fileName), `receipt_404_shows_unavailable_message`, `receipt_401_shows_generic_message`, `receipt_network_failure_shows_generic_message` (engine throws `IOException`), `second_open_while_downloading_fires_no_request` (request counter == 1), `consume_receipt_returns_to_idle`, `consume_while_downloading_drops_late_result` (generation counter, like `submitGeneration`).
- [x] Run → FAIL. Implement (copy the `submitGeneration` pattern). Run → PASS.
- [x] Commit `feat(shared): BillsStore receipt download state`.

### Task 3: iOS button + QuickLook

**Files:** Modify `iosApp/iosApp/BillsView.swift`.

**Consumes:** `store.receipt`, `openReceipt(proofId:)`, `consumeReceipt()`, `dataFrom(bytes:)`, `ReceiptStateReady/Error/Downloading` via `onEnum(of:)`.

- [x] `ProofRow` gets `downloadingProofId: Int64?`, `onReceipt: () -> Void`; for verified+hasReceipt, a `Button("View receipt")` styled like "Submit a new proof" with `receiptNumber` caption (`.pentFoot`, `Pent.label2`), a `ProgressView` in place while `downloadingProofId == proof.id`, `.disabled` while any download runs.
- [x] `BillsView` collects `store.receipt` alongside `state` (second `async let`). On Ready: write `Data(dataFrom(bytes:))` to `temporaryDirectory/receipts/<fileName>` (create dir, overwrite), set `@State receiptURL`, present with `.quickLookPreview($receiptURL)`, `consumeReceipt()`. On Error: alert with message; Binding setter clears local state synchronously and calls `consumeReceipt()`. `.onDisappear { store?.consumeReceipt() }`. Write failure → reuse generic message alert.
- [x] Build: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' build` → BUILD SUCCEEDED.
- [x] Commit `feat(ios): view paid receipt from a verified payment proof`.

### Task 4: Android proofs list + receipt viewer

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills/BillsScreen.kt`
- Create: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills/ReceiptViewer.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/ui/components/Chips.kt` (add `ProofPending` "In review", `ProofVerified` "Verified", `ProofRejected` "Rejected", colours matching existing Paid/Partial/Overdue semantics)
- Modify: `androidApp/src/main/AndroidManifest.xml`; Create `androidApp/src/main/res/xml/file_paths.xml`

**Consumes:** `BillsUiState.Content.proofs`, `proofStatus`, `store.receipt`, `openReceipt`, `consumeReceipt`.
**Produces:** `fun openReceiptPdf(context: Context, pdf: ReceiptPdf): Boolean` (false when no viewer) in `ReceiptViewer.kt`.

- [x] Proofs section: `SectionHeader("Payment proofs")` + `PentFilledCard` of `ProofRow`s between summary and bill history, hidden when empty. Row mirrors iOS: `myr(amountClaimed)` headline, "Submitted ${dateTimeMedium(submittedAt)}", "Verified as …" when different, rejection reason in error colour + "Submit a new proof" (opens sheet), receipt button + caption + progress like iOS.
- [x] FileProvider `${applicationId}.fileprovider`, `<cache-path name="receipts" path="receipts/" />`. `openReceiptPdf` writes `cacheDir/receipts/<fileName>`, `ACTION_VIEW` `application/pdf` + `FLAG_GRANT_READ_URI_PERMISSION`, catches `ActivityNotFoundException`.
- [x] `LaunchedEffect(receiptState)`: Ready → open (false → toast "No app can open PDFs.") then `consumeReceipt()`; Error → toast message then `consumeReceipt()`.
- [x] Run `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest` → BUILD SUCCESSFUL.
- [x] Commit `feat(android): payment proofs list and paid receipt viewer`.

### Task 5: Verify against local server, PR

- [x] pentana-system on `feat/paid-receipt` with `DB_CONNECTION=sqlite DB_DATABASE=<scratch>/receipt.sqlite` env overrides (confirm `php artisan about` shows sqlite before migrating), migrate + seed a member with one verified-with-receipt proof and one pre-launch verified proof, `php artisan serve --host 0.0.0.0`.
- [x] iOS simulator (localhost) and Android emulator (10.0.2.2): button only on the receipt proof; PDF opens; forced 404 shows message.
- [x] Tick this plan, push, open PR against `main` noting the server PR dependency.
