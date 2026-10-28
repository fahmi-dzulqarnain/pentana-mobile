# Home Redesign — "Needs you" + "Coming up"

**Date:** 2026-10-28
**Status:** Draft (design) → awaiting approval, then implementation plan
**Repos:** `pentana-mobile` (iOS + Android + `:shared`), `pentana-system` (dashboard API)
**Mockup:** [`2026-10-28-home-redesign-mockup.html`](2026-10-28-home-redesign-mockup.html), which opens in a browser. It shows
six screens: iOS busy, all clear and dark, Android busy, empty and loading.

## Why
Today Home is four glass cards that look nearly the same, stacked under a date: Dues, Next lunch, Activities and Payment proofs.
Each card has the same icon, title and chevron, so nothing stands out. The one thing a member has to *do* looks just like
things that are only information. Fahmi's verdict: it "looks plain / dated". This redesign keeps the data but changes the
arrangement. Home answers two questions in order: **what do I need to do?** and **what's coming up?**

The direction came from 5 A/B rounds and a design critique, both held on 2026-10-28:
- **Layout:** to-dos plus a timeline. A hero dues card and a bento of coloured tiles both lost.
- **To-dos:** stacked rows, which beat side-by-side cards.
- **Coming up:** calendar date chips, which beat a dotted timeline.
- **Look:** calm glass, which beat an indigo brand header.
- **All clear:** a celebration card, which beat a quiet stats strip.

## Goals
- The member sees what needs action first, ordered by urgency, and can act with one tap.
- One calm, consistent look on iOS (Liquid Glass) and Android (Material 3 tonal), with the same structure on both.
- Rejected payment proofs and committee replies come up on Home. Today a rejected member gets a push and then has
  nowhere to see it again (iOS critique 2026-10-20, item 1.2).
- WCAG AA contrast on every text and button, touch targets of at least 44pt (48dp), dark mode and Dynamic Type / font scaling.

## Non-goals
- No new tabs and no change to Bills, Lunch or Activities screens, except the deep-link entry points named below.
- No in-app payment. Payments stay manual: transfer, then upload proof, then the committee approves.
- No full support centre in the app. The only new support screen is a small ticket thread view (below) reached
  from Home. Creating new tickets stays on the web.
- No visual changes outside Home. Theme tokens are reused, not redefined.

## Anti-goals (a failure even if it "works")
- **"Pay" that looks like checkout.** Any wording or flow that suggests the app takes payment.
- **Saying something twice.** An item shown in both "Needs you" and "Coming up", or two all-clear messages.
- **Rows longer than 2 lines.** Wrapped text, or a button squeezing the title. That was the first revision's failure.
- **Mismatched platforms.** iOS and Android disagreeing on *which* to-dos show or in what order. That logic is shared.

## Screen structure (top to bottom)

1. **Date + greeting.** "Wednesday, 28 October" in secondary 13pt, then "Hi, {first name}" at 22pt bold. That is smaller
   than today's large title, so the first to-do outranks it. The first name is the first word of `UserDto.name`, which the
   session already holds.
2. **Needs you** *(only when there is at least one to-do)*. The header is "Needs you" plus the count in secondary text.
   Below it is one grouped surface with up to **3** to-do rows. With more than 3, a footer row reads "+N more · {next item's
   title}" and expands the list in place.
3. **All-clear card** *(only when there are no to-dos)*. It replaces section 2 and keeps today's "You're all clear"
   card: a 🎉 symbol in a green circle, "You're all clear" and "No dues, no votes waiting. Nice." There is no header and
   no count line.
4. **Coming up.** The header is "Coming up", above one grouped surface:
   - **Proof in review** *(only when the member's pending proofs cover all of the outstanding dues; see "Dues and proofs in
     review")*. The row has a `clock.fill` icon in the proof tint, with "Proof in review · MYR {pending}" and "Dues clear once
     the committee approves". It has a chevron and no button, and opens Bills.
   - **A lunch.** If `next_lunch` is not a to-do, show it. If it is a to-do, show `other_lunch` when present: the
     earliest other upcoming lunch, usually one already voted on. The row shows a date chip, "Lunch · {menu}" and a status
     pill (Voted / Closed).
   - **Next activity** (registered or waitlisted). The row shows a date chip, the title, a status pill and the start time.
   - **Footer row** "✨ N activities open to join ›" when `open_activities_count > 0`. It opens Activities.
   - **Empty** (no rows and no open activities): "Nothing coming up" / "No lunches or activities you've joined yet."
     plus a "Browse activities" button.
5. **Loading:** a skeleton of sections 2 and 4 (two rows each) under the real date and greeting. It replaces today's
   centred spinner. An error keeps today's `EmptyStateView` with pull to refresh.

### To-do row
- **Anatomy:** a 36pt domain icon tile, then the **title** (15pt semibold, 1 line), then the **detail** (13pt
  secondary, 1 line), then a **cue button** on the trailing edge. **Hard limit of 2 lines.** Title and detail each
  truncate with "…". The row's minimum height is 62pt.
- **One tap target:** tapping anywhere on the row does the row's action. The button is a visual cue only and is not a
  separate target. Screen readers read the row as one element: "{title}, {detail}. {button label}, button."
- **Button style (all rows the same):** light mode is white on PENTANA indigo `#1B1464` (15.8:1). Dark mode is
  `#14121F` on light orange `#FBB04C` (10:1). The label is 12.5pt semibold in a capsule. On Android it becomes a
  `FilledTonalButton`-shaped pill in the same colours.
- **Large text:** at accessibility text sizes (iOS `.accessibility1`+, Android font scale ≥ 1.5) the button moves
  below the text and the 2-line limit is lifted. Truncating at those sizes would hide the content.

### To-do types (in urgency order)

| # | When it shows | Icon / tint | Title | Detail | Button | Tap goes to |
|---|---|---|---|---|---|---|
| 1 | Member has a **rejected proof** (rule below) | `exclamationmark.triangle.fill` / bad | Proof rejected | MYR {amount_claimed} · {admin_note} | Resubmit | Submit-proof sheet (existing `SubmitProofView` / Android equivalent) |
| 2 | `total_outstanding − pending_proofs_amount > 0` | `creditcard.fill` / dues | **MYR {total − pending} due** (rounded money, dues colour) | {unpaid_count} bills · MYR {credit} credit, or, with a pending proof, "MYR {pending} in review" | Pay & upload | Bills tab |
| 3 | `next_lunch.is_open && !responded` | `fork.knife` / lunch | Vote for {Ddd} lunch | {N} menus · closes {Ddd} | Vote | Lunch tab, focused on that lunch's vote |
| 4 | `support_replies_waiting > 0` | `bubble.left.and.text.bubble.right.fill` / proof | Committee replied | {subject of the oldest waiting ticket}, or "{N} tickets waiting on you" | Reply | Native ticket thread (one ticket) or the waiting-tickets list (2+); see "Support ticket view" |

"{N} menus" comes from `next_lunch.menus.length`. With one menu, the detail shows that menu's name instead.
"closes {Ddd}" comes from `next_lunch.vote_deadline`. With no deadline, the detail drops that part.

### Dues and proofs in review
A pending proof isn't allocated until the committee approves it, so `total_outstanding` still includes money the member
has already sent. Home must never ask them to "Pay & upload" that money again.
- **Pending proofs cover the dues** (`pending_proofs_amount ≥ total_outstanding > 0`): there is no Dues to-do. A "Proof in
  review" row goes at the top of Coming up. It is not a to-do, so "all clear" can still show.
- **Pending proofs cover part of the dues:** the Dues to-do shows only the remainder (`total − pending`), and the
  detail line says "MYR {pending} in review".
- **No pending proofs:** the Dues to-do behaves as in the table.

### Support ticket view (new, iOS + Android)
A minimal native screen, so "Committee replied" works without the web login. It is built on the existing API
(`GET /api/v1/support-tickets`, `GET …/{id}`, `POST …/{id}/replies`).
- **Waiting list** (shown only when 2+ tickets wait on the member): one row per ticket with the subject, the last-message
  preview and the time. Tapping a row opens the thread.
- **Thread:** messages oldest first, committee messages and the member's own visually distinct. Attachments show
  as a tappable file chip that opens the existing attachment endpoint. The reply box at the bottom takes text only.
  After sending, the ticket leaves "waiting on member" and Home's count drops on the next refresh.
- **Out:** creating tickets, sending attachments and the resolve action all stay on the web.
- **Logic:** a shared `SupportStore` in `:shared`, following the `LunchStore` / `HomeStore` pattern, with a new
  `SupportRepository` and DTOs.
- **Other entry point:** tapping a support-reply push notification opens the same thread.

### Status mapping in "Coming up"
- **Lunch:** responded → "Voted" (ok pill); closed and not responded → "Closed" (neutral pill). An open, unvoted lunch
  is a to-do and never appears here.
- **Activity:** registered → "Registered" (ok); waitlisted → "Waitlisted" (warn). The time comes from `starts_at` in the device's time zone.
- **Date chips:** a 40pt-wide tile. The header strip shows the weekday (lunch: orange `#F7931E` with `#14121F` text, 8.0:1;
  activity: green `#0B6E42` with white text, 6.3:1). Below it is the day number in rounded bold 17pt. The tile background
  is `Pent.surface`, which is not white in dark mode.

## Visual system
- **Tokens:** reuse `Pent.*` (iOS) and the Android theme. Two values change: **secondary label** goes to `#4A475E` (light)
  and `#C4C1D8` (dark) for contrast on glass, and a new **ok-strong** `#0B6E42` is used for chip strips and green pill text
  in light mode (5.5:1 on its tint).
- **Section headers:** 15pt semibold primary label in sentence case, used for both sections. This replaces the 11pt
  uppercase caption, which failed at 3.2:1.
- **iOS:** each section is one `pentGlass(20)` surface with hairline separators between rows, over `AmbientBackground`.
- **Android:** each section is one `surfaceContainer` card (16dp radius, no shadow) with circular icon tiles and the
  same rows. It uses the existing `ListItems.kt` inset-row style where it fits.
- **Icons:** SF Symbols (iOS) and Material Symbols Rounded (Android). The mockup uses emoji as placeholders only.

## Data and logic

### API changes (`pentana-system`, `DashboardController@show`)
Add, without changing any existing field:
- `next_lunch.vote_deadline`: the lunch's `deadline` as ISO 8601, or null.
- `other_lunch`: the same shape as `next_lunch`, or null. It is the earliest published lunch in the same 14-day window
  whose id differs from `next_lunch`.
- `bills.pending_proofs_amount`: the sum of `amount_claimed` over the member's `pending` proofs, as a 2-dp string.
- `rejected_proof`: `{ id, amount_claimed, admin_note, rejected_at }` or null. **Rule:** the member's most recent
  proof by `created_at`, if that proof's status is `rejected` *and* `rejected_at` is within the last 14 days. Once the
  member submits a newer proof, the rejected one is no longer "most recent", so the row disappears.
- `support_reply_ticket`: `{ id, subject }` of the oldest `waiting_on_member` ticket, or null. The "Committee replied"
  row uses it for its detail line and to open the thread directly.
- `support_replies_waiting` already exists, and so does `pending_proofs_count` (kept for older app builds).

**Migration (Fahmi approved adding a column):** add a nullable `rejected_at` timestamp to `payment_proofs`. Set it in
`PaymentReviewActions::reject`, the same place that writes `status` and `admin_note`. Backfill existing rejected rows
from `updated_at`. Prod and dev share the Supabase database, so this runs once, through the normal deploy migration step.

**Shared DTOs:** add to `DashboardDto` `supportRepliesWaiting`, `supportReplyTicket`, `otherLunch` and `rejectedProof`. Add
`pendingProofsAmount` to `DashboardBillsDto`, and `menus` and `voteDeadline` to `DashboardLunchDto`. Every new field is
nullable or defaulted, so a newer app still parses an older API.

Update the feature tests for `/api/v1/dashboard` to cover the new fields. They run on SQLite, so check the date comparison
against Postgres (Supabase) too.

### Shared logic (`:shared`, `HomeDisplay.kt`)
Both platforms render from one shared function, so they can't drift:

```kotlin
sealed interface HomeTodo { RejectedProof, Dues, LunchVote, SupportReply }   // each carries its display data
data class HomeSections(
    val todos: List<HomeTodo>,          // urgency order, uncapped
    val proofInReview: String?,         // pending amount when proofs cover all dues, else null
    val comingUpLunch: DashboardLunchDto?,   // next_lunch, or other_lunch when next_lunch is a to-do
    val comingUpActivity: DashboardActivityDto?,
    val openActivitiesCount: Int,
)
fun homeSections(dashboard: DashboardDto): HomeSections
```

The UI applies the cap of 3 visible plus "+N more" (it is a presentation choice). `duesCleared()` is replaced by
`todos.isEmpty()`. Note that this changes behaviour: a **pending** proof alone no longer blocks "all clear", because it
waits on the committee, not the member. It shows as the "Proof in review" row in Coming up instead.
Shared tests in `commonTest` cover:
- the urgency ordering;
- choosing between the next lunch and the other lunch;
- each to-do's show and hide conditions;
- the three dues and pending-proof cases, with money compared as decimals, not strings;
- the empty state.

## Platform files touched
- **iOS:** `HomeView.swift` (rewrite of the content view; `HomeCard` is removed), `PentanaTheme.swift` (token tweaks),
  the deep-link entry points into Lunch and the submit-proof sheet (Home gets its store from `session.makeBillsStore()`),
  and a new support thread view and list view.
- **Android:** `feature/home/HomeScreen.kt`, `HomeViewModel.kt`, the theme colours, and a new `feature/support` thread
  screen and list screen.
- **Shared:** `DashboardDtos.kt`, `HomeDisplay.kt`, the new `SupportRepository`, `SupportStore` and support DTOs, and tests.
- **Backend:** `DashboardController.php` and its test, the `rejected_at` migration plus backfill, and `PaymentReviewActions::reject`.

## Decisions (Fahmi's)
- Layout, to-do style, chip style, calm glass and the celebration card all come from the A/B rounds.
- **iOS and Android together,** with the same design on both.
- **Rejected proofs and committee replies are to-do rows.**
- Contrast fix: indigo buttons with white text, and rows hard-limited to 2 lines.
- **A proof in review never produces "Pay & upload".** The app shows an "in review" state, and the API sends the
  pending amount.
- **"Committee replied" opens a small native ticket view,** not the web, because the web would need its own sign-in.
- **A new `rejected_at` column,** backfilled from `updated_at`.

## Builder's calls (made during design; change freely)
- The 14-day window and the "most recent proof" rule for the rejected-proof row.
- The urgency order: rejected proof, then dues, then vote, then reply.
- The cap of 3 with "+N more" expanding in place, not navigating.
- The partial-cover rule: the Dues to-do shows the remainder. The "Proof in review" row sits in Coming up, not in Needs you.
- Showing `other_lunch` in Coming up when the next lunch is a to-do, so a lunch already voted on stays visible.
- The support view's scope: text replies only, no new tickets, no resolve, no sending attachments.
- The empty-state copy is "Nothing coming up", not "next 2 weeks", because activities aren't limited to 14 days.
- Greeting uses the first word of `name`.
- The Lunch tab deep link may land on the lunch list if focusing a single lunch isn't supported yet.
