# PENTANA Android (Material 3) — Design Spec

**Date:** 2026-06-27
**Status:** Approved (design) → ready for implementation plan
**Repo:** `pentana-mobile`

## Goal

Stand up a brand-new **Jetpack Compose** Android app (`androidApp/`) against the existing
`:shared` KMP module, implementing the **Material 3 / Material You** design handoff at full
UI parity with the SwiftUI iOS app.

## Scope

**In:** All nine screens + chrome + theme, wired to the existing shared repositories, email/password
login. This is the same first-pass scope the iOS app shipped before passkeys/push were added.

**Out (deferred, consistent with how iOS was built):**
- **Passkeys** (Android Credential Manager) — `PasskeyRepository` exists in `:shared` but stays unused.
- **Push** (FCM) — needs *new backend work* (the Laravel API is APNs-only today); `DeviceTokensRepository` stays unused.
- **Remote image loading** (Coil) — none of these screens display remote images; the proof flow only
  *uploads* bytes. Add Coil later if an avatar/lunch image is shown.

## Source of truth

- Handoff bundle (external): `~/Downloads/pentana-ios-android-ui-design 2/project/`
  - `PENTANA Android.html` (canvas), `android/*.jsx` (components/screens), `android/tokens.css` (design tokens)
  - `uploads/DESIGN_BRIEF.md` §11 = the Android Material track
- The handoff is HTML/CSS/JS **prototype** — recreate the *visual output* in Compose, don't port its structure.
- Token values are embedded below so this spec is self-contained (the handoff is not in the repo).

---

## 1. Architecture decisions

| Decision | Choice | Rationale |
|---|---|---|
| Navigation | **State-driven, no nav library** — one `PentanaApp` composable: `Scaffold` + `NavigationBar`, `rememberSaveable` selected tab; sheets are `ModalBottomSheet` driven by state | IA is flat (4 tabs + modal sheets, no pushed detail screens). Mirrors the iOS `TabView` + `.sheet` structure. Zero extra deps (YAGNI vs `navigation-compose`). |
| State management | AndroidX `ViewModel` + `StateFlow`, collected with `collectAsStateWithLifecycle` | Survives config change/process death; idiomatic; mirrors iOS `SessionStore`/`@Published`. |
| Dependency injection | **Manual** — an `AppContainer` built in `Application`, passed down; ViewModels via a small factory | Same lightweight manual wiring as iOS. No Hilt/Koin for ~9 screens. |
| Token storage | **`EncryptedSharedPreferences`** (`androidx.security:security-crypto`) | Synchronous (fits the `TokenStore` interface), encrypted at rest = parity with the iOS Keychain. Fallback: plain `SharedPreferences` if security-crypto causes build friction (bearer token is server-revocable). |
| Threading | Add **`kotlinx-coroutines-android`** | Every `:shared` repo wraps its call in `withContext(Dispatchers.Main)`; Android needs the Main dispatcher. Ktor suspends for IO (OkHttp's own threads), so there is no main-thread blocking. |

---

## 2. Module & build setup

New module **`androidApp/`** (parallels `iosApp/`).

- `settings.gradle.kts`: add `include(":androidApp")`.
- Plugins: `com.android.application`, `org.jetbrains.kotlin.android`, **`org.jetbrains.kotlin.plugin.compose`** (required on Kotlin 2.1).
- `applicationId` + `namespace` = `my.silentmode.pentana`; `compileSdk = 36`, `minSdk = 26`, `targetSdk = 35`; JVM 17; `buildFeatures { compose = true }`.
- `implementation(project(":shared"))`.
- Version catalog (`gradle/libs.versions.toml`) additions:
  - `androidx.activity:activity-compose`
  - `androidx.compose:compose-bom` (platform) → `compose.ui`, `compose.ui.tooling(-preview)`, `material3`, `material-icons-extended` (or Lucide-equivalent vectors)
  - `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android` (version.ref `coroutines`)
  - `androidx.security:security-crypto`
  - Compose compiler plugin (`kotlin.plugin.compose`, version.ref `kotlin`)
- `AndroidManifest.xml`: `INTERNET` permission; `usesCleartextTraffic="true"` *(dev only — the API is plain-HTTP on the LAN IP; mirrors the iOS ATS exception. Remove for a release build pointing at HTTPS)*; single `MainActivity` (`enableEdgeToEdge()`), adaptive launcher icon.

---

## 3. Platform seams

- **`PrefsTokenStore : TokenStore`** — `EncryptedSharedPreferences`-backed `get/save/clear` (synchronous).
- **`AppConfig`** — `baseUrl` mirrors iOS: dev `http://192.168.0.177:8000/api/v1` (update per network; `ipconfig getifaddr en0`), prod `https://pentana.silentmode.net/api/v1`.
- **`AppContainer`** — builds `ApiClient(AppConfig.baseUrl, PrefsTokenStore(context), engine = null)` (→ OkHttp via `HttpEngine.android.kt`) and the 6 used repositories (Auth, Dashboard, Bills, Lunch, Activities, Notifications).
- **Photo picker** — `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())`; read the returned `Uri` via `ContentResolver.openInputStream(...).readBytes()`; pass bytes + filename to `BillsRepository.submitPaymentProof(...)`.

---

## 4. Design system → Compose (`ui/theme/`)

`tokens.css` is a full M3 role set. Map to a Compose `ColorScheme` + extras. **Dynamic color on API 31+**
(`dynamicLightColorScheme`/`dynamicDarkColorScheme`) with the brand seed below as fallback (brief §11.1).

### 4.1 `Color.kt` — brand `ColorScheme` (light · dark)

| Role | Light | Dark |
|---|---|---|
| primary / onPrimary | `#9A4A00` / `#FFFFFF` | `#FFB784` / `#532300` |
| primaryContainer / on | `#FFDCC2` / `#3A1A00` | `#763500` / `#FFDCC2` |
| secondary / onSecondary | `#4B4196` / `#FFFFFF` | `#C8BFFF` / `#2C2278` |
| secondaryContainer / on | `#E5DEFF` / `#150A5E` | `#433A8F` / `#E5DEFF` |
| tertiary / tertiaryContainer / on | `#006C4C` / `#88F8C5` / `#002115` | `#6BDBAA` / `#005138` / `#88F8C5` |
| error / onError / errorContainer / on | `#BA1A1A` / `#FFFFFF` / `#FFDAD6` / `#410002` | `#FFB4AB` / `#690005` / `#93000A` / `#FFDAD6` |
| surface / onSurface | `#FFF8F4` / `#211A14` | `#19120C` / `#EFE0D5` |
| onSurfaceVariant / outline / outlineVariant | `#50453B` / `#837469` / `#D6C3B6` | `#D6C3B6` / `#9F8D81` / `#50453B` |
| surfaceContainerLowest→Highest | `#FFFFFF` `#FCF1E9` `#F6ECE3` `#F1E6DD` `#EBE0D7` | `#130D08` `#221A13` `#261E16` `#312820` `#3C332A` |
| surfaceDim / surfaceBright | `#E8E0D9` / `#FFF8F4` | `#19120C` / `#41372F` |
| scrim | `rgba(0,0,0,.4)` | `rgba(0,0,0,.6)` |

### 4.2 Domain + status colors (outside the stock scheme)

Carried via a `PentanaColors` data class exposed through a `LocalPentanaColors` `CompositionLocal`
(each is a color + container + on-container triple). **Status is never color-only** — always paired with a label/icon.

| Token | Light (color · container · on) | Dark (color · container · on) |
|---|---|---|
| dues (blue) | `#15489E` · `#D9E2FF` · `#001A43` | `#AFC6FF` · `#1A3878` · `#D9E2FF` |
| lunch (orange) | `#9A4A00` · `#FFDCC2` · `#3A1A00` | `#FFB784` · `#763500` · `#FFDCC2` |
| activ (green) | `#006C4C` · `#88F8C5` · `#002115` | `#6BDBAA` · `#005138` · `#88F8C5` |
| proof (purple) | `#5B3FD0` · `#E6DEFF` · `#1C0066` | `#CBBEFF` · `#43308F` · `#E6DEFF` |
| ok | `#006C4C` · `#88F8C5` | `#6BDBAA` · `#005138` |
| warn | `#8A5000` · `#FFDDB3` | `#FFB95C` · `#5E3F00` |
| bad | `#BA1A1A` · `#FFDAD6` | `#FFB4AB` · `#93000A` |

> Note from the handoff: the **proofs purple is derived** (no purple token exists in the PENTANA web
> system) — flagged for review but kept for parity with iOS.

### 4.3 `Type.kt` — M3 type scale (Roboto Flex)

Display 48/36 · Headline 30/26/22 · Title 20/16/14 · Body 16/14/12.5 · Label 14/12/11, with the
weights/letter-spacing from `tokens.css`. Plus money styles: `moneyLarge` 34/600/-0.5, `moneyMedium`
18/600, and a tabular-figures modifier (`fontFeatureSettings = "tnum"`) for all money/counts.
Bundle Roboto Flex as a downloadable/asset font (fallback to system Roboto).

### 4.4 `Shape.kt`

M3 shape scale: small 8dp, medium 12dp, large 16dp (cards), extra-large 28dp (sheets/hero); buttons fully
rounded (24dp). The Home greeting hero uses an **expressive asymmetric** shape `RoundedCornerShape(28,28,28,10)`.

---

## 5. Component inventory (`ui/components/`)

Build these once; screens compose them. M3 mapping + key specs from the handoff:

- **PentTopBar** — large top app bar: action row (avatar lead 40dp `secondaryContainer` initials, bell
  trailing in a `BadgedBox`; `Badge` = error/onError, caps at "9+", hidden at 0) then optional headline
  title. Home uses the no-title variant (its hero carries the greeting).
- **PentNavBar** — `NavigationBar`, 4 destinations (Home `house`, Bills `receipt-text`, Lunch `utensils`,
  Activities `calendar-days`), active **pill** indicator (`secondaryContainer`), labels always shown.
- **DomainStatCard** (Home) — `ElevatedCard` (surfaceContainerLow, 16dp radius): tonal leading icon (46dp,
  domain container), title (titleLarge), trailing chevron, detail slot.
- **StatusChip** — `AssistChip`-style, **color + label** (+ optional dot/check/clock/outline). Kinds:
  paid, partial, unpaid, overdue, registered, waitlisted, open, closed, votenow, responded (see CHIP map).
- **PentListItem** + **SectionHeader** — `Card`-grouped rows (leading icon, headline, supporting, trailing
  value/chip, hairline divider except last); section header in `primary`, titleSmall.
- **SingleSelectOptionRow** — `RadioButton` row (lunch voting): 22dp control, title (600 when chosen), sub.
- **Buttons** — filled (primary), tonal (secondaryContainer), outlined, text, **destructive** (error text);
  height 48, radius 24. **ExtendedFAB** (primaryContainer, 56dp, shadow-3) for Bills "Submit proof".
- **PentTextField** / **PentDropdown** / **PentCheckbox** — M3 filled `TextField` (surfaceContainerHighest,
  2dp underline primary/error, supporting text), `ExposedDropdownMenuBox`, `Checkbox`.
- **PhotoPickerTile** — outlined dashed surface ("Add receipt photo" / camera) ↔ selected state
  (thumbnail + "Photo selected" + filename·size + clear).
- **EmptyState** — 76dp rounded tonal icon, titleLarge, body, optional action.
- **PentSnackbar** — inline errors (onSurface bg) + retry/dismiss action. Use the `Scaffold` `SnackbarHost`.
- **ModalSheetScaffold** — `ModalBottomSheet` wrapper: drag handle, title, scroll body, pinned footer button.
- **Spinner / loading + error blocks** — centered spinner + "Loading…"; error = EmptyState `cloud-off` + Retry.

---

## 6. Screens (content · states · data binding)

Each tab screen has a ViewModel exposing `UiState = Loading | Error(retry) | Content(data)`; pull-to-refresh
(`PullToRefreshBox`) on all four. Money is the pre-formatted 2dp string from the API, prefixed `MYR `.

### 6.1 Login → `AuthRepository.login(email, password, "Android")`
Centered mark (76dp), "PENTANA" headline (tracking 0.12em), "Member sign in". Email (leading mail) +
Password (leading lock) filled fields. Full-width filled **Sign in** (disabled until both non-empty; loading
→ in-button spinner). Error → field supporting text ("The provided credentials are incorrect.") + Snackbar.
"Need access? Claim your account on the web" hint. On success → session set → tabs.

### 6.2 Home → `DashboardRepository.dashboard()` (`DashboardDto`)
No-title top bar + **expressive greeting hero** (primaryContainer, asymmetric shape, date + "Hi, {first name}").
Four `DomainStatCard`s, each tappable to switch to its tab:
1. **Dues** — `bills.totalOutstanding` → "MYR {x} outstanding" (or "No dues outstanding"); sub "Credit MYR {credit} · {unpaidCount} unpaid".
2. **Next lunch** — `nextLunch`: "{date} · {menu}" + chip (`votenow` if open && !responded / `responded` / "Voting closed"); or "None scheduled".
3. **Activities** — `nextActivity`: title + chip(myStatus) · date, plus "{openActivitiesCount} open"; or "No upcoming registrations".
4. **Payment proofs** — `pendingProofsCount` → "{n} awaiting review" / "Nothing pending".
**Celebratory:** when no dues + nothing pending, replace the dues card with an "You're all clear" `party-popper` card.

### 6.3 Bills → `BillsRepository.summary()` + `.bills()`
Prominent summary card (secondaryContainer, 24dp): "TOTAL OUTSTANDING" + `moneyLarge`, then available credit +
unpaid count. "Bill history" section → `Card` of `PentListItem`s (month headline, "Due X · Paid Y" supporting,
trailing outstanding money + `StatusChip(status)`). **Extended FAB "Submit proof"** → 6.4. Empty: "No bills yet."

### 6.4 Submit payment proof *(ModalBottomSheet)* → `BillsRepository.submitPaymentProof(bytes, name, amount, note?)`
Amount (MYR) field, Note (optional, multiline), `PhotoPickerTile`. Submit disabled until **amount + photo**.
States: idle · submitting (spinner) · error ("Upload failed. Please try again.") · success → dismiss + refresh Bills (+ Home).

### 6.5 Lunch → `LunchRepository.lunches()`, `.chooseOption(id, optionId)` / `.markNotAttending(id)`
Per-lunch `FilledCard`: header (menu title + "{date} · {caterer}") + status chip. **Open** → `RadioButton`
group of `options` + a "Not attending" row (single-select; selecting calls respond; reflect `myMealOptionId`).
**Closed** → locked summary supporting text ("Ordering closed — you ordered {X}." / "…not attending." / "…no order placed.").
Footer deadline row (`deadline`). Empty: "No upcoming lunches."

### 6.6 Activities → `ActivitiesRepository.activities()`, `.register(id, answers)`, `.cancel(id)`
Per-activity `ElevatedCard`: title + **spots chip** (`spotsLeft` → "{n} spots left" / "Full"; `myStatus` →
Registered/Waitlisted chip), date (calendar) + location (map-pin), short plain-text description excerpt
(strip rich-text HTML), state-driven action:
- open + none → **Register** (→ 6.7 if `questions` non-empty, else register directly)
- registered → "You're registered" ✓ + **Cancel registration** (destructive)
- waitlisted → "Waitlisted — #{waitlistPosition}" + **Cancel**
- closed (`!isOpen`) → "Registration closed"
Empty: "No upcoming activities."

### 6.7 Activity registration *(ModalBottomSheet — dynamic form)* → `.register(id, answers: Map<String,String>)`
Optional tertiaryContainer blurb. Render `questions: List<QuestionDto>` by `type`: **text**→TextField,
**textarea**→multiline, **select**→ExposedDropdownMenuBox(`options`), **checkbox**→Checkbox (submit "true"/"false").
`required` marked `*`; **Submit disabled until all required answered**. States: idle · submitting · error
("Registration failed. Please check your answers."). Success → dismiss + refresh Activities. *(This is the one
medium-high custom piece flagged in the handoff — a small server-driven form builder.)*

### 6.8 Profile *(ModalBottomSheet, from avatar)* → `UserDto` (held in session)
Avatar (84dp initials), name, email. "Membership" `Card`: Category (`memberCategory`), Birthday (`birthday`),
Credit balance (`credit`, money, ok color). Separate `Card` with **Sign out** (destructive, `log-out`) →
`AuthRepository.logout()` → back to Login.

### 6.9 Notifications *(ModalBottomSheet, from bell)* → `NotificationsRepository.notifications()` / `.markAllRead()`
`Card` of rows (leading icon, title, body, relative time from `createdAt`, **unread** = leading dot +
surfaceContainerHigh bg). **Icon source:** `NotificationDto` has **no type field** (`id, title, body, read,
createdAt`), so derive the leading icon/color by **keyword-matching the title** (lunch→utensils/lunch, activity/
spot→calendar/activ, "you're in"/promoted→party-popper/activ, proof/payment→file-check/proof, cancelled→x-circle/bad)
with a generic bell/secondary fallback. **Opening the sheet marks all read** (`markAllRead()`) and clears the bell
badge. "Mark all read" text action. Empty: "No notifications yet."

### 6.10 List states
Loading (centered spinner + "Loading…"), empty (per-tab EmptyState + Refresh), error (EmptyState `cloud-off` +
Retry tonal; Home copy: "Couldn't load your summary. Pull to refresh.").

---

## 7. Chrome & session behavior
- `SessionViewModel`: holds `UserDto?`, `unreadCount`, bootstrap (auto-login via `AuthRepository.isLoggedIn()` →
  `me()`), login, logout; refreshes badge from `notifications().unreadCount`.
- Top bar avatar → Profile sheet; bell `BadgedBox` → Notifications sheet (clears badge on open).
- Edge-to-edge + predictive back (`enableEdgeToEdge()`, `targetSdk 35`).

## 8. Motion & accessibility
- M3 expressive spring physics; shared-axis for tab/sheet transitions; subtle badge animation on new unread.
- System font scale (text reflows; never truncate money/status), TalkBack content descriptions (avatar,
  "Notifications, {n} unread", status chips, option rows), 48dp touch targets, AA contrast in both schemes.

## 9. App icon
Adaptive icon: foreground = `pentana-mark.png` (from handoff `assets/`), orange background layer, + a
monochrome layer for Android 13+ themed icons. Place under `res/mipmap-anydpi-v26` + density buckets.

## 10. Testing
- JVM unit tests (`androidApp/src/test`) for **pure logic only**: money/relative-time formatting, the
  dynamic-form required-validation, dashboard→card-model mapping, and rich-text→excerpt stripping.
- Networking is already covered by `:shared`'s MockEngine tests — do **not** duplicate.
- Build gate: `./gradlew :androidApp:assembleDebug` green; `./gradlew :shared:allTests` still green.

## 11. Proposed file structure
```
androidApp/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/res/ (mipmap adaptive icon, themes.xml, font)
  src/main/kotlin/my/silentmode/pentana/
    MainActivity.kt
    PentanaApp.kt                  // Scaffold + NavigationBar + sheet host
    App.kt (Application)           // builds AppContainer
    core/   AppConfig.kt  AppContainer.kt  PrefsTokenStore.kt
    ui/theme/   Color.kt  Type.kt  Shape.kt  Theme.kt  PentanaColors.kt
    ui/components/  (the inventory in §5)
    ui/session/ SessionViewModel.kt
    feature/login/      LoginScreen.kt  LoginViewModel.kt
    feature/home/       HomeScreen.kt   HomeViewModel.kt
    feature/bills/      BillsScreen.kt  BillsViewModel.kt  SubmitProofSheet.kt
    feature/lunch/      LunchScreen.kt  LunchViewModel.kt
    feature/activities/ ActivitiesScreen.kt ActivitiesViewModel.kt RegistrationSheet.kt
    feature/profile/    ProfileSheet.kt
    feature/notifications/ NotificationsSheet.kt
  src/test/kotlin/...  (formatting/validation/mapping tests)
```

## 12. Open items / risks
- **security-crypto** can be finicky across AGP/compile versions — fallback to plain `SharedPreferences` is acceptable.
- **Roboto Flex** as a variable font in Compose — if the variable axis is awkward, fall back to static Roboto weights.
- **Compose BOM ↔ compileSdk 36 / Kotlin 2.1.21** — pick a BOM known-compatible with the Kotlin compiler plugin at plan time.
- `usesCleartextTraffic` is **dev-only** (LAN HTTP). A release build must point at the HTTPS prod URL and drop it.
