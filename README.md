# PENTANA Mobile

Native member app for [PENTANA](https://github.com/fahmi-dzulqarnain/pentana-system).
**Kotlin Multiplatform** for shared logic + **native UI per platform** (SwiftUI on iOS
first; Jetpack Compose on Android later). Talks to the Laravel `/api/v1` Sanctum API.

## Status

| Area | State |
|------|-------|
| KMP shared module | ✅ networking, DTOs, repositories, token store, MockEngine tests |
| iOS app (SwiftUI) | ✅ Liquid Glass redesign · **Home · Bills · Lunch · Activities** + Profile & Notifications sheets |
| On-device / LAN login | ✅ ATS exception + LAN base URL wired and verified |
| Passkeys | ✅ built — sign-in + on-device registration; **needs the live HTTPS domain to function** (see [Passkeys](#passkeys-sign-in--on-device-registration)) |
| Android (Compose) | ⬜ later — the shared module already builds for `androidTarget` |

Both the shared module (`./gradlew :shared:allTests`) and the iOS app (`xcodebuild`) build
and pass clean.

## Toolchain

Pinned versions — the Gradle wrapper **is committed**, so you do not generate it.

| Tool | Version |
|------|---------|
| JDK | 17 |
| Gradle | 8.11.1 (wrapper committed) |
| Kotlin | 2.1.21 (≥ 2.1.21 needed for Xcode 16.3+/26.x — [KT-75781](https://youtrack.jetbrains.com/issue/KT-75781)) |
| Android Gradle Plugin | 8.7.2 |
| Ktor | 3.0.3 |
| kotlinx-coroutines | 1.9.0 |
| kotlinx-serialization | 1.7.3 |
| Android SDK | compileSdk 36 · minSdk 26 · targetSdk 35 |
| Xcode | 26.x (16+ required — the project uses synchronized folder groups) |

KMP targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`. iOS gets a **static**
`Shared.framework`.

## Repository layout

```
shared/                                   # Kotlin Multiplatform module
  src/commonMain/kotlin/.../shared/
    ApiClient.kt          # Ktor HttpClient (explicit engine) + base URL + ensureSuccess/ApiException
    HttpEngine.kt         # expect defaultHttpEngine()  (actuals: Darwin on iOS, OkHttp on Android)
    TokenStore.kt         # synchronous interface + InMemoryTokenStore (iOS injects a Keychain impl)
    AuthRepository.kt     # login / me / logout
    BillsRepository.kt    # bills / summary / payment-proofs / submitPaymentProof (multipart)
    LunchRepository.kt    # lunches / respond (chooseOption / markNotAttending)
    ActivitiesRepository.kt # activities / register(id, answers) / cancel
    DashboardRepository.kt  # GET /dashboard aggregate
    NotificationsRepository.kt # list / markAllRead
    PasskeyRepository.kt  # passkey login/register options+verify, list/delete
    model/                # @Serializable DTOs (Dtos, Lunch, Activity, Dashboard, Notification, Passkey)
  src/iosMain/...         # HttpEngine.ios.kt  -> Darwin.create()
  src/androidMain/...     # HttpEngine.android.kt -> OkHttp.create()
  src/commonTest/...      # PentanaApiTest.kt — repositories against Ktor MockEngine

iosApp/
  Info.plist                              # partial plist (ATS exception), merged via INFOPLIST_FILE
  iosApp.entitlements                     # Associated Domains (webcredentials:) for passkeys
  iosApp.xcodeproj
  iosApp/
    iosAppApp.swift        # @main; owns the SessionStore
    SessionStore.swift     # @MainActor DI: ApiClient + repos + auth state
    AppConfig.swift        # API base URL + passkey relying party  <-- set these
    KeychainTokenStore.swift  # TokenStore backed by the iOS Keychain
    PasskeyManager.swift   # ASAuthorization bridge (the only non-shared passkey piece)
    PentanaTheme.swift  PentanaComponents.swift   # design system (tokens, glass, components)
    ContentView.swift      # root switch + glass TabView (Home / Bills / Lunch / Activities)
    HomeView.swift  LoginView.swift  BillsView.swift  SubmitProofView.swift
    LunchView.swift  ActivitiesView.swift  RegisterActivityView.swift
    ProfileView.swift  NotificationsView.swift
```

## 1. Run the backend API

The app needs the Laravel server reachable. From the `pentana-system` repo:

```bash
php artisan serve --host=0.0.0.0 --port=8000
```

`--host=0.0.0.0` (not the default `127.0.0.1`) is what makes it reachable from a physical
device / the simulator over your LAN. Verify from another shell:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST http://<mac-LAN-ip>:8000/api/v1/login -H 'Accept: application/json'
# 422 = route reachable (validation error, as expected)
```

Find your Mac's LAN IP with `ipconfig getifaddr en0`.

## 2. Point the app at your server

Edit `iosApp/iosApp/AppConfig.swift`:

```swift
static let baseURL = "http://<mac-LAN-ip>:8000/api/v1"   // e.g. http://192.168.0.177:8000/api/v1
```

The LAN IP works for **both** the simulator and a physical device on the same Wi-Fi. Plain
HTTP is allowed by the ATS exception in `iosApp/Info.plist`
(`NSAppTransportSecurity → NSAllowsLocalNetworking`), which keeps App Transport Security on
for the public internet. For a real build, swap in the deployed Cloudflare **HTTPS** URL —
no exception needed.

## 3. Build & run the iOS app

Open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator (or your device), and **Run**.

The shared framework is built automatically: a **Run Script** build phase (ordered *before*
Compile Sources) runs

```bash
cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

and `FRAMEWORK_SEARCH_PATHS` points at `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
`ENABLE_USER_SCRIPT_SANDBOXING = NO` on the app target lets that script invoke Gradle.

> **If the build fails with “No such module 'Shared'” or Gradle “command not found”:**
> Xcode's build environment must be able to find Java. If `java` isn't on the PATH Xcode
> sees, add `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` to the top of the Run Script
> phase. SourceKit also reports “No such module 'Shared'” in the editor until the framework
> has been built once — build the app and it clears.

### Command-line build (CI / sanity check)

```bash
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  ENABLE_USER_SCRIPT_SANDBOXING=NO
```

## 4. Build & test the shared module

```bash
./gradlew :shared:allTests                              # all targets (incl. iOS sim)
./gradlew :shared:testDebugUnitTest                     # Android/JVM unit tests only
./gradlew :shared:iosSimulatorArm64Test                 # iOS simulator tests only
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # just link the framework
```

## KMP ↔ Swift interop rules (read before extending)

These are load-bearing — the static framework and Kotlin/Native are strict:

1. **`@Throws(Exception::class)` on every `suspend` fun exposed to Swift.** Without it,
   Kotlin/Native aborts the process when an exception crosses into Swift instead of throwing.
2. **`withContext(Dispatchers.Main)` inside those suspend funs.** The static-framework engine
   needs a real dispatcher and the completion must land back on main for Swift's `async`.
3. **Pass an explicit Ktor engine per platform** (`HttpEngine.kt` expect/actual → Darwin /
   OkHttp). The static linker dead-code-eliminates auto-registered engines → crash on first
   request otherwise. Tests inject `MockEngine`.
4. **`TokenStore` is a *synchronous* interface** so Swift's `KeychainTokenStore` conforms
   cleanly (no suspend bridging for storage).
5. **Avoid nullable `Long`/`Int` on the wire where possible**; Swift sees them boxed as
   `KotlinLong?` / `KotlinInt?` and must read `.int64Value` / `.int32Value`.
6. **Tests must `Dispatchers.setMain(UnconfinedTestDispatcher())`** (`@BeforeTest`) because the
   repositories hop to `Dispatchers.Main`.

## Adding a feature slice (the established pattern)

Each member feature is built in three small, independently-verifiable steps:

1. **API** (`pentana-system`): controller + JSON resource + route under `auth:sanctum`, with
   a Feature test. Actions act on `auth()->user()` — never a client-supplied id.
2. **Shared** (`shared/`): `@Serializable` DTO(s) + a `Repository` (following rules 1–2 above)
   + MockEngine tests in `PentanaApiTest.kt`. Verify with `:shared:allTests` + link.
3. **iOS** (`iosApp/`): add the repo to `SessionStore`, a SwiftUI view, and a tab in
   `ContentView`. New `.swift` files in `iosApp/iosApp/` are picked up automatically
   (synchronized folder group). Verify with `xcodebuild`.

Bills, Lunch, and Activities were each built this way — copy the closest one.

## Conventions

- **Money** is fixed-2dp **strings** end-to-end (e.g. `"70.00"`) to avoid float drift; parse
  to a decimal type only where you need arithmetic.
- API responses are wrapped as `{ "data": ... }` (`DataEnvelope<T>`).
- The Sanctum bearer token is stored via `TokenStore`; `AuthRepository.login()` saves it and
  every subsequent request sends it automatically.

## Passkeys (sign-in + on-device registration)

The app can register a passkey on the phone and sign in with it, reusing the backend's
`spatie/laravel-passkeys` setup over JSON APIs. Full design + rationale:
[`docs/superpowers/specs/2026-06-27-mobile-passkeys-design.md`](docs/superpowers/specs/2026-06-27-mobile-passkeys-design.md).

**Pieces**
- **Backend** (`pentana-system`): `POST /api/v1/passkeys/login/{options,verify}` (public,
  discoverable → token), `…/register/{options,verify}` + `GET`/`DELETE /passkeys` (auth).
  Stateless — the challenge is cached under a random `state` token. AASA is published at
  `public/.well-known/apple-app-site-association`.
- **Shared**: `PasskeyRepository` (HTTP only; WebAuthn options/credential cross as JSON strings).
- **iOS**: `PasskeyManager` (ASAuthorization), `AppConfig.passkeyRelyingParty`, the
  `webcredentials:` entitlement, a "Sign in with a passkey" button on Login, and a Passkeys
  section in Profile.

> **Passkeys only work against the live HTTPS domain.** iOS derives the WebAuthn origin from
> the Associated Domain, so the simulator/LAN-IP setup can't run the ceremony — the code builds
> and the rest of the app is unaffected, but the passkey buttons fail at the OS step until the
> domain is up.

### Deploy checklist (all must be true before passkeys work)
1. `pentana.silentmode.my` serves the app over **HTTPS**; `APP_URL=https://pentana.silentmode.my`.
2. `https://pentana.silentmode.my/.well-known/apple-app-site-association` returns
   `{"webcredentials":{"apps":["7778Y2522V.my.silentmode.pentana.iosApp"]}}` (ensure nginx
   serves `.well-known` — many configs deny dotfolders by default).
3. The app's `AppConfig.baseURL` points at `https://pentana.silentmode.my/api/v1` (not the LAN IP)
   for the passkey calls, and `passkeyRelyingParty` stays `pentana.silentmode.my`.
4. A signed build whose team (`7778Y2522V`) has the **Associated Domains** capability enabled for
   the App ID `my.silentmode.pentana.iosApp`.

## Android (later)

The shared module already compiles for `androidTarget` with an OkHttp engine. Adding the app
means a new `androidApp` module with Jetpack Compose UI that reuses the same repositories —
no shared-code changes expected.
