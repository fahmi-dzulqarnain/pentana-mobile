# Home Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Home on iOS and Android around "Needs you" to-dos and "Coming up", backed by new dashboard API fields
and a small native support ticket view.

**Architecture:** The backend (`pentana-system`) adds the dashboard fields, a `rejected_at` column and a ticket id on
support-reply pushes. The shared KMP module turns the dashboard into `HomeSections` with one pure function, so both
platforms show the same to-dos in the same order. It also gets support stores. Each platform then renders natively:
SwiftUI Liquid Glass on iOS, Compose Material 3 tonal surfaces on Android.

**Tech Stack:** Laravel 12 / PHPUnit / Filament 4 (backend); Kotlin Multiplatform + Ktor + SKIE (shared); SwiftUI on
iOS 26; Jetpack Compose M3.

**Spec:** `docs/superpowers/specs/2026-10-28-home-redesign-design.md`. Mockup: `docs/superpowers/specs/2026-10-28-home-redesign-mockup.html`.

**Branches:** two repos, one branch each. Both are named `feat/home-redesign` and cut from `main` in a fresh worktree.
`pentana-mobile`'s current checkout is `feat/shared-session-manager` with uncommitted work, so don't touch it. Fresh
`pentana-system` worktrees need `composer install` and a copied `public/build`. Ship the backend first: every new DTO
field is defaulted, so an older API is safe.

## Global Constraints
- Every new API field is additive. Existing field names and shapes don't change, and `pending_proofs_count` stays.
- Every new Kotlin DTO field is nullable or defaulted (`= null`, `= 0`, `= "0.00"`, `= emptyList()`).
- Money is compared as integer cents, never as strings or floats.
- To-do urgency order is fixed: RejectedProof → Dues → LunchVote → SupportReply. At most 3 are shown, plus "+N more · {next title}".
- To-do row: title and detail are each 1 line, truncated with "…". The minimum height is 62pt (iOS) / 62dp (Android). The whole row is one tap target. At accessibility text sizes (iOS `.accessibility1`+, Android fontScale ≥ 1.5) the button moves below the text and the line limit is lifted.
- Cue button: light mode `#FFFFFF` on `#1B1464`; dark mode `#14121F` on `#FBB04C`. 12.5pt semibold capsule.
- Secondary label: light `#4A475E`, dark `#C4C1D8`. New ok-strong `#0B6E42` (light) for chip strips and green pill text.
- Date chip strip: lunch `#F7931E` with `#14121F` text; activity `#0B6E42` with `#FFFFFF` text. The tile body uses the surface token (not white in dark mode).
- Section headers: 15pt semibold primary label, sentence case: "Needs you" + count, and "Coming up".
- Copy (exact):
  - "You're all clear" / "No dues, no votes waiting. Nice."
  - "Nothing coming up" / "No lunches or activities you've joined yet." / "Browse activities"
  - "Proof in review · MYR {x}" / "Dues clear once the committee approves"
  - Buttons: "Resubmit", "Pay & upload", "Vote", "Reply"
- Prod and dev share the Supabase Postgres database. The migration runs only through the normal deploy, never from a laptop.

## Review Focus
1. **Pending proofs that exactly match, exceed, or partly cover dues** ("120.00" vs "120", or pending > outstanding). Expected: no "Pay & upload" when pending ≥ outstanding, and the remainder shown otherwise. Test: Task 4 `dues_*` cases.
2. **A rejected proof with a null `admin_note` or null `rejected_at`** (older rows, backfill gaps). Expected: the detail shows "MYR {amount}" alone, and a null `rejected_at` never shows the row. Tests: Task 2 `test_rejected_proof_without_rejected_at_is_not_shown` and Task 4 `rejected_proof_without_reason_shows_amount_only`.
3. **A new app against the old API** (dashboard JSON without any new fields). Expected: it parses, and Home renders the to-dos it can. Test: Task 4 `parses_legacy_dashboard_payload`.
4. **Replying to the committee and then going back to Home.** Expected: the "Committee replied" row is gone without a manual pull to refresh. Tests: Task 5 `send_marks_ticket_needs_reply`, and Tasks 7 and 9 refresh Home when the support view closes.
5. **The voting deadline across time zones and the deadline day.** Expected: "closes {Ddd}" uses the device's local weekday from the ISO offset, and a past deadline means not open (the server's `is_open` wins). Test: Task 2 `test_next_lunch_includes_vote_deadline_as_iso8601`.

---

## Backend — `pentana-system`

### Task 1: `rejected_at` on payment proofs

**Files:**
- Create: `database/migrations/2026_10_28_000002_add_rejected_at_to_payment_proofs_table.php`
- Modify: `app/Models/Payment/PaymentProof.php` (fillable and casts)
- Modify: `app/Filament/Resources/FeePayments/Actions/PaymentReviewActions.php` (`reject()` action closure)
- Test: `tests/Feature/PaymentVerificationTest.php`

**Interfaces:** Produces the `payment_proofs.rejected_at` nullable timestamp, cast to `datetime`.

- [ ] **Step 1: Write the failing test** `test_rejecting_stamps_rejected_at`. It calls `rejectPayment` with
  `['admin_note' => 'Receipt is unreadable']` (the same setup as `test_admin_rejects_with_a_reason_that_is_shown_afterwards`),
  then asserts `$proof->fresh()->rejected_at` is not null and is within 5 seconds of `now()`.
- [ ] **Step 2: Run** `php artisan test --filter=test_rejecting_stamps_rejected_at`. Expected: FAIL (unknown column, or null).
- [ ] **Step 3: Implement.**
  - The migration adds `$table->timestamp('rejected_at')->nullable()->after('verified_at')`.
  - In `up()`, after adding the column, it backfills: `DB::table('payment_proofs')->where('status','rejected')->whereNull('rejected_at')->update(['rejected_at' => DB::raw('updated_at')])`.
  - `down()` drops the column.
  - Add `rejected_at` to `$fillable` and to `casts()` as `datetime`.
  - In `reject()`, add `'rejected_at' => now()` to the `update([...])`.
- [ ] **Step 4: Run** `php artisan test --filter=PaymentVerificationTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: stamp rejected_at when a payment proof is rejected`.

### Task 2: Dashboard API fields

**Files:**
- Modify: `app/Http/Controllers/Api/V1/DashboardController.php`
- Test: `tests/Feature/Api/DashboardApiTest.php`

**Interfaces:**
- Consumes: `payment_proofs.rejected_at` (Task 1).
- Produces (inside `data`; JSON names are exact):
  - `bills.pending_proofs_amount`: string, 2 dp, the sum of `amount_claimed` over the member's `pending` proofs.
  - `next_lunch.vote_deadline`: ISO 8601 with offset, or null.
  - `other_lunch`: same shape as `next_lunch` (including `vote_deadline`), or null.
  - `rejected_proof`: `{id, amount_claimed (2dp string), admin_note|null, rejected_at (ISO)}` or null.
  - `support_reply_ticket`: `{id, subject}` for the oldest `WaitingOnMember` ticket by `last_message_at`, or null.

- [ ] **Step 1: Write the failing tests:**
  - `test_bills_include_pending_proofs_amount`: pending proofs of 20 and 15.5, plus a rejected proof of 99 → `'35.50'`.
  - `test_next_lunch_includes_vote_deadline_as_iso8601`: deadline `2026-11-05 10:00` in app time → it equals `$lunch->deadline->toIso8601String()`, and a lunch with a null deadline gives `null`.
  - `test_other_lunch_is_the_earliest_upcoming_lunch_that_is_not_next_lunch`: tomorrow's lunch is responded and Friday's is open and unresponded → `next_lunch.id` = Friday and `other_lunch.id` = tomorrow. With a single lunch, `other_lunch` is null.
  - `test_rejected_proof_is_the_latest_proof_when_rejected_within_14_days`: one rejected proof with `rejected_at` 3 days ago → it's returned with `admin_note`.
  - `test_rejected_proof_hidden_when_a_newer_proof_exists`: a pending proof created after the rejected one → `rejected_proof` is null.
  - `test_rejected_proof_hidden_after_14_days`: `rejected_at` 15 days ago → null.
  - `test_rejected_proof_without_rejected_at_is_not_shown`: rejected with `rejected_at` null → null.
  - `test_support_reply_ticket_is_the_oldest_waiting_ticket`: two `WaitingOnMember` tickets and one `NeedsReply` → it returns the older waiting ticket's `{id, subject}`.
  - Extend `test_empty_state_returns_nulls_and_zeroes` with `pending_proofs_amount` = `'0.00'`, and nulls for `other_lunch`, `rejected_proof` and `support_reply_ticket`.
- [ ] **Step 2: Run** `php artisan test --filter=DashboardApiTest`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement.**
  - Extract the lunch array building in `nextLunch()` into `private function lunchPayload(Lunch $lunch, User $user): array`, and add `vote_deadline` to it.
  - `nextLunch()` returns the chosen `Lunch` model; `show()` builds `next_lunch` and `other_lunch` from the same `$lunches` collection, where `other_lunch` is the first one whose id ≠ the target's.
  - Add `private function rejectedProof(User $user): ?array`. It takes the latest proof by `created_at` then `id`, and returns it only if `status === 'rejected'` and `rejected_at >= now()->subDays(14)`.
  - Add `private function supportReplyTicket(User $user): ?array`.
  - Format money with the same `number_format(…, 2, '.', '')` the controller already uses.
- [ ] **Step 4: Run** `php artisan test --filter=DashboardApiTest`. Expected: PASS.
- [ ] **Step 5: Postgres check.** These tests run on SQLite. Run the `rejected_at` 14-day comparison and the
  `pending_proofs_amount` sum once against a local Postgres. Use a separate `.env.pgtest`, never the shared Supabase.
  Expected: same results.
- [ ] **Step 6: Commit** `feat(api): dashboard pending amount, other lunch, rejected proof, support reply ticket`.

### Task 3: Ticket id on support-reply pushes

**Files:**
- Modify: `app/Notifications/SupportNotification.php` (`toApn`)
- Test: `tests/Feature/Support/SupportPushPayloadTest.php` (create)

**Interfaces:** Produces the APNs custom key `ticket_id` (int) on every support push.

- [ ] **Step 1: Write the failing test** `test_support_push_carries_ticket_id`. Build `new SupportReplyFromCommittee($ticket, $message)`, call `toApn($member)`, and assert `$apn->custom['ticket_id'] === $ticket->id`.
- [ ] **Step 2: Run** `php artisan test --filter=SupportPushPayloadTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add `->custom('ticket_id', $this->ticket->id)` to the `ApnMessage` chain. Update the class
  docblock line "The push carries no deep-link data" to say support pushes carry `ticket_id`.
- [ ] **Step 4: Run** `php artisan test`. Expected: the full suite PASSES.
- [ ] **Step 5: Commit** `feat(push): support pushes carry ticket_id for in-app deep link`.

---

## Shared — `pentana-mobile/shared`

### Task 4: Dashboard DTOs and `homeSections()`

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/model/DashboardDtos.kt`
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeDisplay.kt`
- Create: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/HomeDisplayTest.kt`
- Modify: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/HomeStoreTest.kt` (drop the `duesCleared` tests)

**Interfaces:**
- Consumes: the Task 2 JSON.
- Produces:

```kotlin
// DashboardDtos.kt: new and extended fields (all defaulted)
DashboardDto(+ otherLunch: DashboardLunchDto? = null, rejectedProof: DashboardRejectedProofDto? = null,
             supportRepliesWaiting: Int = 0, supportReplyTicket: DashboardSupportTicketDto? = null)
DashboardBillsDto(+ pendingProofsAmount: String = "0.00")
DashboardLunchDto(+ menus: List<DashboardMenuDto> = emptyList(), voteDeadline: String? = null)
data class DashboardMenuDto(val caterer: String? = null, val menu: String? = null)
data class DashboardRejectedProofDto(val id: Long, val amountClaimed: String, val adminNote: String? = null, val rejectedAt: String? = null)
data class DashboardSupportTicketDto(val id: Long, val subject: String)

// HomeDisplay.kt
sealed interface HomeTodo {
    data class RejectedProof(val proofId: Long, val amount: String, val reason: String?) : HomeTodo
    data class Dues(val amountDue: String, val unpaidCount: Int, val credit: String, val inReview: String?) : HomeTodo
    data class LunchVote(val lunch: DashboardLunchDto) : HomeTodo
    data class SupportReply(val count: Int, val ticketId: Long?, val subject: String?) : HomeTodo
}
data class HomeSections(
    val todos: List<HomeTodo>,
    val proofInReview: String?,           // "120.00" when pending ≥ outstanding > 0, else null
    val comingUpLunch: DashboardLunchDto?,
    val comingUpActivity: DashboardActivityDto?,
    val openActivitiesCount: Int,
)
fun homeSections(dashboard: DashboardDto): HomeSections
internal fun moneyCents(amount: String): Long      // "120" / "120.5" / "120.00" → 12000 / 12050 / 12000
internal fun formatCents(cents: Long): String      // 3550 → "35.50"
```

`dashboardLunchStatus`, `dashboardActivityStatus` and `DashboardActivityStatus` stay. Delete `duesCleared`.

- [ ] **Step 1: Write the failing tests in `HomeDisplayTest`** (build DTOs directly; no HTTP):
  - `todos_follow_urgency_order`: all four present → the types are `[RejectedProof, Dues, LunchVote, SupportReply]`.
  - `dues_fully_covered_by_pending_moves_to_proof_in_review`: outstanding "120.00", pending "120" → no `Dues`, and `proofInReview == "120.00"`.
  - `dues_partly_covered_shows_remainder`: outstanding "120.00", pending "50.00" → `Dues(amountDue = "70.00", inReview = "50.00")`, and `proofInReview == null`.
  - `dues_without_pending_has_no_in_review`: outstanding "120.00", pending "0.00" → `Dues(amountDue = "120.00", inReview = null)`.
  - `pending_more_than_outstanding_is_still_in_review`: outstanding "40.00", pending "60.00" → no `Dues`, and `proofInReview == "40.00"`.
  - `zero_outstanding_has_no_dues_and_no_in_review`.
  - `open_unvoted_lunch_is_a_todo_and_other_lunch_is_coming_up`: `nextLunch` is open and unresponded, and `otherLunch` is set → `LunchVote(nextLunch)` and `comingUpLunch == otherLunch`.
  - `voted_lunch_is_coming_up_not_a_todo`.
  - `support_reply_todo_carries_oldest_ticket`: `supportRepliesWaiting = 2` plus a ticket → `SupportReply(2, id, subject)`.
  - `rejected_proof_without_reason_shows_amount_only`: `adminNote = null` → `RejectedProof(reason = null)`.
  - `all_clear_when_only_pending_proof`: dues fully covered, nothing else → `todos.isEmpty()` and `proofInReview != null`.
  - `parses_legacy_dashboard_payload`: decode the existing `HomeStoreTest.dashboardJson` (none of the new fields) with the module's `Json` config, then `homeSections()` → one `Dues("120.00")` and one `LunchVote`.
  - `money_cents_round_trips`: `moneyCents("120")==12000`, `moneyCents("0.5")==50`, `formatCents(3550)=="35.50"`.
- [ ] **Step 2: Run** `./gradlew :shared:allTests`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the DTO fields (`@SerialName` with snake_case names exactly as in Task 2) and the functions
  above. Mark `duesCleared` `@Deprecated("Use homeSections().todos.isEmpty()")` rather than deleting it, because both
  apps still call it until Tasks 6 and 8. Remove its tests from `HomeStoreTest`.
- [ ] **Step 4: Run** `./gradlew :shared:allTests :androidApp:assembleDebug`. Expected: BUILD SUCCESSFUL (deprecation warnings only).
- [ ] **Step 5: Commit** `feat(shared): homeSections for Needs you / Coming up`.

### Task 5: Support repository and stores

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/model/SupportDtos.kt`
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/SupportRepository.kt`
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/SupportStore.kt`
- Create: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/SupportStoreTest.kt`

**Interfaces:**
- Consumes: the existing `GET /support-tickets`, `GET /support-tickets/{id}`, `POST /support-tickets/{id}/replies {body}` and `GET` of a message's `attachment_url`.
- Produces:

```kotlin
data class SupportTicketDto(id: Long, type: String, subject: String, status: String,
    lastMessageAt: String? = null, lastMessageFromCommittee: Boolean = false)
data class SupportMessageDto(id: Long, body: String, fromCommittee: Boolean, isMine: Boolean,
    createdAt: String? = null, attachmentUrl: String? = null)
data class SupportTicketDetailDto(id: Long, type: String, subject: String, status: String,
    lastMessageAt: String? = null, closedAt: String? = null, messages: List<SupportMessageDto> = emptyList())

class SupportRepository(client: ApiClient) {
    suspend fun tickets(): List<SupportTicketDto>
    suspend fun ticket(id: Long): SupportTicketDetailDto
    suspend fun reply(ticketId: Long, body: String): SupportMessageDto
    suspend fun attachment(url: String): ByteArray   // bearer-authenticated download
}

sealed interface SupportListUiState { Loading; Error(message: String); Content(tickets: List<SupportTicketDto>) }
class SupportListStore(repo: SupportRepository) {      // waiting-on-member tickets only
    val state: StateFlow<SupportListUiState>; fun load(); fun clear()
}
sealed interface SupportThreadUiState { Loading; Error(message: String); Content(ticket: SupportTicketDetailDto) }
class SupportThreadStore(repo: SupportRepository, ticketId: Long) {
    val state: StateFlow<SupportThreadUiState>; val sending: StateFlow<Boolean>
    suspend fun send(body: String): Boolean     // false on blank body or failure; on success appends + status = "needs_reply"
    fun load(); fun clear()
}
```

The spec names a single `SupportStore`. It is split into list and thread stores because the screens' state differs.
Both live in `SupportStore.kt`, and both follow the scope and teardown pattern of `NotificationsStore`. Wrap each JSON
response in `DataEnvelope`, and copy `DashboardRepository`'s auth header code.

- [ ] **Step 1: Write the failing tests** (MockEngine, in the same style as `HomeStoreTest`):
  - `list_shows_only_waiting_on_member`: three tickets with statuses `waiting_on_member`, `needs_reply` and `closed` → one in Content.
  - `thread_loads_messages_oldest_first`.
  - `send_marks_ticket_needs_reply`: POST returns 201 with a message → Content has the appended message, `status == "needs_reply"`, and `send` returns true.
  - `send_blank_body_is_rejected_without_request`: `"  "` → false, and the engine records zero POSTs.
  - `send_failure_keeps_messages_and_returns_false`: a 500 → false, and the messages are unchanged.
  - `thread_error_state_on_404`: `Error("Couldn't load this conversation.")`.
- [ ] **Step 2: Run** `./gradlew :shared:allTests`. Expected: FAIL.
- [ ] **Step 3: Implement** the DTOs, the repository and the stores. Error copy: list → "Couldn't load your messages.", thread → "Couldn't load this conversation."
- [ ] **Step 4: Run** `./gradlew :shared:allTests`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(shared): support ticket repository and stores`.

---

## iOS — `pentana-mobile/iosApp`

### Task 6: iOS Home rewrite

**Files:**
- Modify: `iosApp/iosApp/PentanaTheme.swift`
  - `Pent.label2` → light `0x4A475E`, dark `0xC4C1D8`
  - add `Pent.okStrong = Color(light: 0x0B6E42, dark: 0x34C77E)`
  - add `Pent.ctaBg = Color(light: 0x1B1464, dark: 0xFBB04C)` and `Pent.ctaFg = Color(light: 0xFFFFFF, dark: 0x14121F)`
- Modify: `iosApp/iosApp/HomeView.swift` (rewrite the content; delete `HomeCard`; keep `PentDates`)
- Create: `iosApp/iosApp/HomeComponents.swift` (`TodoRow`, `ComingUpRow`, `DateChip`, `SectionHeader`, `HomeSkeleton`)

**Interfaces:**
- Consumes: `homeSections(dashboard:)` and `HomeTodo` (Task 4, via SKIE as `onEnum(of:)`); `session.makeBillsStore()`; `SubmitProofView`; the user's name from `session`.
- Produces: `HomeView(selection: Binding<Int>)` keeps its signature. A new `@State var supportRoute: SupportRoute?`, which Task 7 fills in.

- [ ] **Step 1: Implement the theme tokens.** Then add `#Preview`s in `HomeComponents.swift` for `TodoRow` (each to-do
  type, light and dark, and at `.accessibility2`, where the button wraps below) and for `DateChip` (lunch and activity).
- [ ] **Step 2: Implement `HomeView`** following the spec's "Screen structure", in order:
  - date and greeting (first word of the name);
  - Needs you (cap 3, "+N more" expands in place);
  - the all-clear card;
  - Coming up (proof in review row, lunch, activity, open-to-join footer, empty state);
  - `HomeSkeleton` for `.loading`.

  Taps:
  - RejectedProof → present `SubmitProofView` with `session.makeBillsStore()`.
  - Dues and the proof-in-review row → `selection = 1`.
  - LunchVote and the coming-up lunch → `selection = 2`.
  - Activity and open-to-join → `selection = 3`.
  - SupportReply → `supportRoute` (a no-op until Task 7).

  Each `TodoRow` is a single `Button` with `.accessibilityElement(children: .combine)`.
- [ ] **Step 3: Build.** Run `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build` with the sandbox disabled. Expected: `** BUILD SUCCEEDED **`.
- [ ] **Step 4: Check in the Simulator** against the mockup, using a member with outstanding dues plus an open lunch, then an all-clear member. Screenshot both, in light and dark. Expected: the layout matches the mockup's busy and all-clear phones, and no row is taller than 2 lines at default text size.
- [ ] **Step 5: Commit** `feat(ios): Home Needs you / Coming up redesign`.

### Task 7: iOS support view and push deep link

**Files:**
- Create: `iosApp/iosApp/SupportViews.swift` (`SupportListView`, `SupportThreadView`, `enum SupportRoute: Identifiable { case list, thread(Int64) }`)
- Modify: `iosApp/iosApp/SessionStore.swift` (`let support = SupportRepository(client:)`, plus `makeSupportListStore()` and `makeSupportThreadStore(ticketId:)`)
- Modify: `iosApp/iosApp/HomeView.swift` (present `supportRoute` as a sheet; call `store.refresh()` on dismiss)
- Modify: `iosApp/iosApp/PushNotifications.swift` (read `ticket_id` in `didReceive`, and publish it through `PushRegistrar.shared.openTicket: ((Int64) -> Void)?`)
- Modify: `iosApp/iosApp/ContentView.swift` (subscribe: `selection = 0`, then present the thread sheet)

**Interfaces:** Consumes `SupportListStore`, `SupportThreadStore` and `SupportRepository.attachment` (Task 5), and the `ticket_id` push key (Task 3).

- [ ] **Step 1: Implement the views.**
  - Thread: messages oldest first; committee bubbles on the leading side in `Pent.surface2`, the member's on the trailing side in `Pent.duesBg`; the time under each.
  - An attachment chip downloads through `attachment(url:)` to a temp file and opens it in `QuickLookPreview`.
  - Composer: a `TextField` with axis `.vertical` and a send button that is disabled while `sending` or the text is blank.
  - The list's rows open the thread.
  - `SupportReply` with `count == 1` and a `ticketId` → `.thread(id)`; otherwise `.list`.
- [ ] **Step 2: Build** (the same command as Task 6). Expected: `** BUILD SUCCEEDED **`.
- [ ] **Step 3: Check in the Simulator** against the local API, using a member with a `waiting_on_member` ticket.
  1. Tap the Home row, reply "Thanks", and close the sheet.
  2. Expected: the Home row is gone. In the admin panel, the ticket shows the reply with status "needs reply".
  3. Then send a push with `xcrun simctl push booted <bundle> payload.json` using `{"aps":{"alert":"x"},"ticket_id":<id>}`. Expected: the thread opens.
- [ ] **Step 4: Commit** `feat(ios): native support thread from Home and push`.

---

## Android — `pentana-mobile/androidApp`

### Task 8: Android Home rewrite

**Files:**
- Modify: the Android theme colours file (wherever `onSurfaceVariant` is defined under `ui/`): `onSurfaceVariant` = `#4A475E` light and `#C4C1D8` dark, plus an `okStrong` extension colour of `#0B6E42`.
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/home/HomeScreen.kt` (rewrite)
- Create: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/home/HomeComponents.kt` (`TodoRow`, `ComingUpRow`, `DateChip`, `SectionHeader`, `HomeSkeleton`)
- Modify: `shared/.../HomeDisplay.kt` (delete the deprecated `duesCleared`)

**Interfaces:**
- Consumes: `homeSections()` and `HomeTodo` (Task 4).
- Produces: `HomeScreen(userName: String, onSwitchTab: (NavDest) -> Unit, onOpenSupport: (Long?) -> Unit, onResubmitProof: () -> Unit)`. Here `null` means the list. `onResubmitProof` opens the existing Android submit-proof flow from `feature/bills`.

- [ ] **Step 1: Implement the components** with `@Preview` for light, dark and `fontScale = 1.5f`.
  - Each section is a `surfaceContainer` `Card` with a 16dp radius and no elevation.
  - Icon tiles are circular.
  - Each row is `Modifier.clickable` across its full width, with `semantics(mergeDescendants = true)`.
- [ ] **Step 2: Implement `HomeScreen`** with the same structure and taps as Task 6 Step 2, mapped to `NavDest`. Wire the new callbacks in `App.kt`; `onOpenSupport` is a no-op until Task 9.
- [ ] **Step 3: Run** `./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:testDebugUnitTest`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Check in the emulator** (10.0.2.2 and a multi-worker `artisan serve`), with the same two members as Task 6, in light and dark. Expected: it matches the mockup's Android phone.
- [ ] **Step 5: Commit** `feat(android): Home Needs you / Coming up redesign`.

### Task 9: Android support screens

**Files:**
- Create: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/support/SupportScreens.kt` (`SupportListScreen`, `SupportThreadScreen`, `SupportViewModel`s)
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/core/AppContainer.kt` (`val support = SupportRepository(client)`)
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/App.kt` (show the support screens full screen, the same way the notifications screen is shown; refresh Home's store on back)

**Interfaces:** Consumes the Task 5 stores. The Android app has no push, so there's no deep link here.

- [ ] **Step 1: Implement** the same behaviour as Task 7 Step 1. Attachments are written to `cacheDir` and opened with
  `ACTION_VIEW` through the app's existing `FileProvider` (see `core/Photo.kt`).
- [ ] **Step 2: Run** `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Check in the emulator** with the same reply flow as Task 7 Step 3, minus the push. Expected: the Home row is gone after replying.
- [ ] **Step 4: Commit** `feat(android): native support thread from Home`.

---

### Task 10: End-to-end check

- [ ] **Step 1:** Seed one member in each state: busy (rejected proof, dues, open lunch, support reply), partly covered,
  fully covered by a pending proof, all clear, and empty. Use the local DB, never Supabase.
- [ ] **Step 2:** On iOS and Android, confirm for each state that the to-dos match in type, order and count, and that
  the Coming up rows match. Note any difference as a bug against Task 6 or Task 8.
- [ ] **Step 3:** Accessibility pass on both platforms:
  - the largest text size (the button wraps below, nothing is clipped);
  - VoiceOver and TalkBack (each row reads as one element);
  - dark mode.
- [ ] **Step 4:** Update the spec's status to "Implemented". Note any builder's calls that changed.
