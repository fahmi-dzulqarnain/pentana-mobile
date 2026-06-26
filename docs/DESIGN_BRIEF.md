# PENTANA Mobile — iOS UI Design Brief / Prompt

> **How to use this document.** Paste it whole into Claude (or your design tool) as the prompt,
> or hand it to a designer. It describes a *real, shipping* iOS app — every screen, its content,
> and its states — and the visual direction we want. The goal is **high‑fidelity mockups**
> (light **and** dark) for each screen plus a small component system, not working code.
>
> **One-line ask:** *Design a stunning, trustworthy iOS 26 member app for PENTANA — a welfare/dues
> membership companion — following Apple's Human Interface Guidelines and leading with the Liquid
> Glass material. Deliver light + dark mockups for every screen below, a component sheet, and the
> tab-bar / nav-bar glass treatment.*

---

## 1. The product

**PENTANA** is the member-facing companion to an organisation's welfare & membership system
(used by staff/members in Malaysia). Members use it to:

- see what they **owe** (monthly welfare dues) and submit **payment proof**,
- **vote** on the daily/weekly catered **lunch** menu,
- **register** for **activities/events** (with per-event questions, capacity & waitlist),
- glance at a **dashboard** that summarises all of the above,
- read **notifications** and manage their **profile**.

It is finance-adjacent, so the design must feel **clear, calm, and trustworthy** — but it's also a
*community* app, so it should feel **warm and a little premium**, not corporate-grey.

**Currency:** Malaysian Ringgit, shown as `MYR 70.00` (always 2 decimal places).
**Locale/tone:** English (en-GB), Malaysian context (names, halal dietary options in lunch, etc.).

---

## 2. Platform & design language  ← the important part

Target **iOS 26**, iPhone-first. Follow the **HIG** pillars — *clarity, deference, depth* — and
lead with **Liquid Glass**.

### Liquid Glass (prioritise)
- Treat glass as the **functional layer that floats above content**: the **tab bar**, **navigation
  bar** items, **toolbars**, **sheets**, primary **buttons**, and the **dashboard summary cards**.
- Use the system Liquid Glass material (translucent, light-refracting, specular, dynamically tinted
  by the content scrolling beneath it). In SwiftUI terms this maps to `glassEffect()`,
  `GlassEffectContainer`, `.buttonStyle(.glass)` / `.glassProminent`, and the tab/nav bars that
  adopt glass automatically — **but design it visually; don't write code.**
- **Restraint is the rule:** glass is for controls and chrome, **content stays legible and opaque
  enough to read.** Never stack glass on glass. Let the wallpaper/content tint the glass rather
  than painting it a flat colour.
- Embrace **concentric, capsule, and continuous-corner** shapes; generous rounding; edge-to-edge
  layouts that respect safe areas.

### Native-first
- Build on standard structures so it's implementable in SwiftUI with minimal custom code:
  **NavigationStack**, **inset-grouped List/Form**, **TabView**, **sheets/`.presentationDetents`**,
  **SF Symbols 6**, **SF Pro** type, full **Dynamic Type**, and **complete dark mode**.
- Money and counts are the data that matters — give numbers typographic weight (rounded/mono-ish
  numerals, larger sizes for headline figures).

### Negotiable (bespoke welcome where it elevates)
- The **Home dashboard hero/cards**, **empty states**, **login**, and small **delight moments**
  (badge animation, a tasteful celebratory state when "all dues cleared") may go beyond stock
  components — as long as they still feel like iOS.

### Please avoid
- Heavy custom chrome that fights the OS; non-system body fonts; fixed hex colours that break in
  dark mode or fail contrast; flat opaque bars where glass is expected; decoration that reduces
  legibility of money/status.

---

## 3. Brand & visual system (propose, don't assume)

- **Wordmark:** "PENTANA" — propose a clean wordmark treatment (the app currently shows it as a
  bold title on login). A simple, confident monogram/app-icon concept is welcome.
- **Accent & semantics:** the app uses **domain-tinted icons** today — keep these as the semantic
  palette and let Liquid Glass + system backgrounds carry the rest:
  - 💳 **Dues / Bills** → blue
  - 🍴 **Lunch** → orange
  - 📅 **Activities** → green
  - 🧾 **Payment proofs** → purple
  - Status colours: success/green (paid, registered), warning/amber (vote now, partial, waitlisted),
    neutral/secondary (closed, nothing pending).
- Provide a **light and dark** palette; ensure status is **never colour-only** (pair with a label/icon).
- **Type:** SF Pro. Define a small scale: large-title (screen titles), headline (card titles &
  money), subheadline, caption.

---

## 4. Information architecture

```
Tab bar (Liquid Glass, 4 tabs):
  Home · Bills · Lunch · Activities

Global chrome (on every tab's nav bar):
  ← top-left:  Profile   (person icon → Profile sheet, holds Sign out)
  → top-right: Bell      (unread badge → Notifications sheet)
```

---

## 5. Screens (content · states · intent)

Design each in **light and dark**. For lists, also show the **empty** and **loading** states.

### 5.1 Login
- **Purpose:** the only unauthenticated screen.
- **Content:** PENTANA wordmark, "Member sign in" subtitle, **Email** field, **Password** field,
  **Sign in** primary button (glass; disabled until both filled).
- **States:** idle · loading (in-button or overlay spinner) · inline error ("The provided
  credentials are incorrect."). Members claim/activate their account on the web first — a subtle
  "Need access? Claim your account on the web" hint is welcome.
- **Notes:** a strong candidate for a bespoke, glassy, brand-forward first impression.

### 5.2 Home — Dashboard  *(landing tab)*
- **Purpose:** one-glance summary; each card jumps to its tab.
- **Content:** greeting **"Hi, {first name}"**, then **four summary cards** (icon · title · 1–2
  detail lines · chevron):
  1. **Dues** — `MYR {outstanding} outstanding` *or* "No dues outstanding"; secondary:
     `Credit MYR {credit} · {n} unpaid`.
  2. **Next lunch** — date + menu name + an action line: **"Vote now"** (amber, if not yet voted) /
     "You've responded" / "Voting closed"; or "None scheduled".
  3. **Activities** — next registered activity title + `Registered/Waitlisted · {date}`, plus
     `{n} open to join`; or "No upcoming registrations".
  4. **Payment proofs** — `{n} awaiting review` / "Nothing pending".
- **States:** loading (centered spinner) · loaded · error ("Couldn't load your summary. Pull to
  refresh.") · pull-to-refresh.
- **Notes:** this is the **hero** — show off Liquid Glass cards floating over a subtle background.
  Consider a celebratory treatment when everything is clear (no dues, nothing pending).

### 5.3 Bills
- **Purpose:** what the member owes + history; entry to submit proof.
- **Content:** a **summary header** (total outstanding · available credit · unpaid count), then a
  **list of bills**: month (e.g. "June 2026"), amount due, amount paid, **outstanding**, and a
  **status pill** — `paid` / `partial` / `unpaid` (+ possibly `overdue`).
- **Primary action:** **Submit payment proof** (button → sheet, 5.4).
- **States:** list, empty ("No bills yet."), loading.

### 5.4 Submit payment proof  *(sheet)*
- **Content:** **Amount (MYR)** field, **Note** (optional, multiline), **Receipt photo** picker
  (shows a "Photo selected" confirm state), **Submit** (disabled until amount + photo).
- **States:** idle · submitting (spinner) · error ("Upload failed. Please try again.") · success →
  dismiss & refresh. Use `.presentationDetents` (medium/large).

### 5.5 Lunch
- **Purpose:** vote on upcoming catered lunches.
- **Content:** list of upcoming lunches; each shows **date**, **menu** name, **caterer**. For an
  **open** lunch: selectable **meal-option rows** (single-choice, checkmark on the chosen one) plus
  a **"Not attending"** row. For a **closed** lunch: a summary line ("Ordering closed — you ordered
  Beef." / "…you marked not attending." / "…no order placed."). Show the **order deadline**.
- **States:** open (votable) · responded · closed · empty ("No upcoming lunches.").
- **Notes:** the option selector is a great candidate for a refined, glassy single-select control.

### 5.6 Activities
- **Purpose:** browse & register for events.
- **Content:** list of upcoming activities; each card: **title**, **date/time**, **location**, a
  **spots-left** indicator, and a short **description** preview (the source is rich text — show a
  clean plain-text/short excerpt). Action depends on state:
  - **Open + not registered:** **Register** button (opens the question form, 5.7, if the event has
    questions; otherwise registers directly).
  - **Registered:** "You're registered" ✓ + **Cancel registration**.
  - **Waitlisted:** "Waitlisted — #{n} in line" + **Cancel registration**.
  - **Closed:** "Registration closed".
- **States:** the four above · empty ("No upcoming activities.") · loading.

### 5.7 Activity registration  *(sheet — dynamic form)*
- **Purpose:** answer an event's custom questions, then register.
- **Content:** a form rendered from a list of questions, each of type **text**, **textarea**,
  **select** (menu picker), or **checkbox**; **required** questions marked with `*`. Optional
  description blurb at top. **Submit** disabled until required questions are answered.
- **States:** idle · submitting · error ("Registration failed. Please check your answers.").

### 5.8 Profile  *(sheet, from top-left)*
- **Content:** large avatar (SF Symbol or initials), **name**, **email**; a **Membership** section
  (category · birthday · `Credit MYR {x}`); and a **Sign out** (destructive) row at the bottom.
- **Notes:** calm, settings-like, inset-grouped. This is where Sign out now lives (it used to be a
  nav-bar button — by design we moved it here).

### 5.9 Notifications  *(sheet, from top-right bell)*
- **Content:** list of notifications — **title**, **body**, **relative time** (e.g. "2h ago").
  Examples: "Lunch published: Nasi Lemak", "New activity: Beach Cleanup", "You're in: …" (waitlist
  promoted), "Activity cancelled: …". Distinguish **unread** (e.g. a leading dot / tint).
- **Behaviour:** opening the list marks all read and clears the bell badge.
- **States:** list · empty ("No notifications yet.") · loading.

---

## 6. Component system (please define both light & dark)

- **Liquid Glass tab bar** (4 items, selected state).
- **Nav bar** with leading **profile** glyph and trailing **bell + unread badge** (badge = small
  red count/dot; define 0, 1–9, 9+).
- **Summary / stat card** (used on Home): domain-tinted icon, title, detail lines, chevron, glass surface.
- **List row** (inset-grouped) and **section header**.
- **Status pill** (paid/partial/unpaid; open/closed; registered/waitlisted) — colour **and** label.
- **Single-select option row** (lunch voting) with chosen checkmark.
- **Primary button** (glass / prominent), **secondary/destructive** button.
- **Form fields** (text, multiline, menu select, toggle/checkbox) per HIG.
- **Photo picker tile** (empty vs "selected").
- **Empty-state** block (icon + line + optional action).
- **Money / number** type style (headline figures vs inline).

---

## 7. Motion & micro-interactions
- Liquid Glass morph on **sheet present/dismiss** and **tab switches**.
- **Bell badge** appear/clear animation; subtle bounce when a new notification arrives.
- **Pull-to-refresh** on Home/Bills/Lunch/Activities.
- Button press / selection feedback; a small celebratory beat when dues hit zero.
- Keep it tasteful and quick — deference over spectacle.

---

## 8. Accessibility (required, not optional)
- **Dynamic Type** to the largest sizes (cards reflow, never truncate money/status).
- **VoiceOver** labels for the profile button, bell ("Notifications, {n} unread"), status pills,
  and option rows.
- **Reduce Transparency / Increase Contrast:** provide the **fallback** for every glass surface
  (solid material + clear borders) — show these variants in the deliverables.
- Hit targets ≥ 44×44 pt; status never conveyed by colour alone; AA contrast for text on glass.

---

## 9. Deliverables
1. High-fidelity mockups, **light + dark**, for: Login, Home, Bills, Submit Proof, Lunch,
   Activities, Activity Registration, Profile, Notifications.
2. The **component sheet** from §6.
3. Close-ups of the **Liquid Glass tab bar** and **nav bar (profile + bell badge)**.
4. **Empty / loading / error** states for the four list screens.
5. A short **rationale** + any proposed **palette, wordmark, and app-icon** concept.
6. Call out any element that would need **significant custom engineering** (vs native), since this
   is a real SwiftUI app and we want it implementable.

---

## 10. Context: this is a real, working app
A native SwiftUI build already exists (Kotlin Multiplatform handles the networking/logic; SwiftUI
the UI). The current screens are functional but visually plain — **your job is to elevate them into
something stunning while keeping the native structure** (NavigationStack, inset List/Form, TabView,
sheets) so the team can implement it with standard components + Liquid Glass modifiers. Treat the
content and states above as the source of truth.

*(Android is the parallel track — see **§11**. Same information architecture and content, rendered
in Material.)*

---

## 11. Android (Material 3 / Material You)

Android is a **parallel track to iOS**: identical information architecture, screens, content, and
states (§4–§5) — only the **rendering language changes**. Where iOS leads with Liquid Glass, Android
leads with **Material 3 (Material You)**, using its newest **Material 3 Expressive** direction
(2025) for shape, motion, and emphasis. Build on **Jetpack Compose Material 3** components so it's
implementable with standard widgets.

### 11.1 Design language
- **Material 3 Expressive:** expressive shapes (the M3 shape scale, including larger/asymmetric
  "expressive" shapes for hero elements), emphasized typography, and springy, physical **motion**.
- **Dynamic color (Material You):** derive tonal palettes from the user's wallpaper on Android 12+,
  with a **brand seed colour** as the fallback scheme. Honour both light & dark.
- **Surfaces, not glass:** Material expresses hierarchy with **tonal surfaces + elevation**
  (surface tints, shadows), **not** translucency. **Do not fake Liquid Glass on Android** — the
  iOS glass chrome becomes elevated/tonal surfaces here. That's the intended, correct divergence.
- **Type:** the Material 3 **type scale** (Display / Headline / Title / Body / Label) in
  Roboto (or Roboto Flex); keep money/numbers emphasised.
- **Shape:** the M3 shape scale (rounded corners, larger radii for cards/sheets); expressive shapes
  reserved for hero/dashboard moments.

### 11.2 Navigation & chrome
- **Bottom `NavigationBar`** for the 4 destinations: Home · Bills · Lunch · Activities (with the
  selected-item pill indicator). On larger/foldable widths, a `NavigationRail` is acceptable.
- **`TopAppBar`** carries the same global chrome as iOS, translated:
  - **Leading:** a circular **avatar** (initials/photo) → opens **Profile** (full screen or
    `ModalBottomSheet`); Sign out lives inside, as on iOS.
  - **Trailing:** a **notifications** action icon wrapped in a **`BadgedBox`** (`Badge` shows the
    unread count) → opens **Notifications**.
- **Back:** support the **predictive back** gesture; draw **edge-to-edge** under the system bars.

### 11.3 iOS → Android component map
| iOS (Liquid Glass / HIG) | Android (Material 3) |
|---|---|
| Liquid Glass tab bar | `NavigationBar` (bottom), pill indicator |
| Nav-bar profile (left) + bell (right) | `TopAppBar` avatar (lead) + `BadgedBox` action (trail) |
| Glass summary cards (Home) | `ElevatedCard` / `FilledCard` with tonal surface + leading icon |
| Inset-grouped List / Form | `Card`-grouped `ListItem`s with section headers |
| Status pill | `AssistChip` / `Badge` (label **and** colour) |
| Single-select lunch option row | `RadioButton` list, or `SingleChoiceSegmentedButtonRow` |
| Primary glass button | `Button` (filled); secondary `FilledTonalButton` / `OutlinedButton` |
| Destructive (Sign out / Cancel) | error-coloured text/`Button` |
| iOS `.sheet` (Submit Proof, Register, Profile, Notifications) | `ModalBottomSheet` (drag handle, partial/expand) |
| Inline error text | `Snackbar` (+ field supporting/error text) |
| Submit payment proof button | `Button`, or an **extended FAB** on the Bills screen |
| Pull-to-refresh | Material `PullToRefresh` |
| Photo picker tile | Outlined picker surface → Android Photo Picker |
| Empty state | centered icon + body + optional `TextButton` |

### 11.4 Screen notes (deltas from iOS only)
- **Login:** centered brand lockup; **filled/outlined `TextField`s**; full-width filled **Sign in**;
  errors via inline supporting text + `Snackbar`.
- **Home:** the four summary items as **Material cards** (tonal surface, domain-tinted leading icon,
  title + detail + trailing chevron/`>`), tappable to switch destinations. Reserve an expressive
  shape/motion for the greeting/hero.
- **Bills:** summary as a prominent card; bills as `ListItem`s with a trailing **status chip**;
  submit-proof as a `Button` or extended FAB.
- **Lunch:** options as a `RadioButton` group (single choice) + a "Not attending" option; closed
  state as supporting text; show the deadline.
- **Activities / Registration:** activity `Card`s with a **spots-left** chip and state-driven
  action; the registration form in a `ModalBottomSheet` with Material `TextField` / dropdown
  (`ExposedDropdownMenuBox`) / `Checkbox`.
- **Profile / Notifications:** `ModalBottomSheet` or full screen; notifications use `ListItem`s with
  an unread indicator; opening clears the `Badge`.

### 11.5 Motion & accessibility (Android specifics)
- **Motion:** Material **container transform** for card → detail, **shared-axis** for tab/sheet
  transitions, and M3 Expressive **spring** physics; tasteful `Badge` animation on new notifications.
- **Accessibility:** support the system **font scale** (text reflows), **TalkBack** content
  descriptions (avatar, "Notifications, {n} unread", status chips), **48×48 dp** minimum touch
  targets, and AA contrast across dynamic-colour light/dark schemes.

### 11.6 Android deliverables
Mirror §9 for Android: light + dark (and ideally a **dynamic-colour** example) mockups for all nine
screens, the Material component sheet, the `NavigationBar` + `TopAppBar`(badge) close-ups, and the
empty/loading/error states. Keep the two platforms **recognisably the same product** — same IA,
colour semantics, and tone — while each feels **native to its OS** (Liquid Glass on iOS, tonal
Material on Android). Call out any deliberate cross-platform divergence.
