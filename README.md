# PENTANA Mobile

Native member app for [PENTANA](https://github.com/fahmi-dzulqarnain/pentana-system).
**Kotlin Multiplatform** for shared logic + **native UI per platform** (SwiftUI on iOS
first; Jetpack Compose on Android later). Talks to the Laravel `/api/v1` Sanctum API.

## Status

- ✅ **MOB-1 — KMP shared module** (this commit): networking, DTOs, repositories, token store, tests.
- ⬜ **MOB-2 — SwiftUI iOS app** (Login → Bills → Submit proof).
- ⬜ Android (Compose) — later; the shared module is already Android-ready.

> ⚠️ **This scaffold was authored without a local Kotlin/Gradle build** (it was written
> alongside the backend API). Expect to run the first build yourself and adjust plugin/
> dependency versions to match your installed toolchain. The Kotlin source (DTOs/repos/
> client) is the stable part; the Gradle wiring is the most likely thing to need a nudge.

## Prerequisites

- **JDK 17**
- **Android Studio** (Ladybug+) with the Kotlin Multiplatform plugin, or IntelliJ IDEA
- **Xcode 15+** (for the iOS framework + the SwiftUI app)
- **Gradle 8.11+** (only needed once, to generate the wrapper — see below)

## First-time setup

This repo intentionally does **not** commit the Gradle wrapper binary. Generate it once:

```bash
# Option A — you have Gradle installed:
gradle wrapper --gradle-version 8.11.1

# Option B — just open the project in Android Studio; it generates the wrapper on import.
```

Then build + run the shared module's tests:

```bash
./gradlew :shared:assemble          # compile all targets
./gradlew :shared:allTests          # run commonTest on every target
# or a single target while iterating:
./gradlew :shared:testDebugUnitTest         # Android unit tests
./gradlew :shared:iosSimulatorArm64Test     # iOS simulator tests
```

## Module layout

```
shared/src/
  commonMain/kotlin/my/silentmode/pentana/shared/
    ApiClient.kt          # Ktor client (platform engine auto-selected) + base URL + status handling
    TokenStore.kt         # interface + InMemoryTokenStore (iOS app injects a Keychain impl)
    AuthRepository.kt     # login / me / logout
    BillsRepository.kt    # bills / summary / payment-proofs / submitPaymentProof (multipart)
    model/Dtos.kt         # @Serializable DTOs mirroring /api/v1 (money as fixed-2dp strings)
  commonTest/...          # repository tests against Ktor MockEngine
```

## Configuration

The API base URL is passed into `ApiClient` (no hardcoding):

```kotlin
val client = ApiClient(
    baseUrl = "https://pentana.example.com/api/v1",   // prod (Cloudflare); or http://<mac-LAN-ip>:8000/api/v1 for local dev
    tokenStore = keychainTokenStore,                  // iOS provides a Keychain-backed impl
)
val auth = AuthRepository(client)
val bills = BillsRepository(client)
```

- **iOS Simulator** can reach `http://localhost:8000/api/v1` (Laravel `php artisan serve`).
- A **physical device** needs the Mac's LAN IP or the deployed Cloudflare HTTPS URL.

## Auth & token storage

`AuthRepository.login()` stores the Sanctum bearer token via `TokenStore`; subsequent calls
send it automatically. The **iOS app implements `TokenStore` in Swift backed by the Keychain**
(written in MOB-2) and injects it — so the token is stored securely without fragile
Kotlin/Native Keychain code.

## Notes

- Kotlin `suspend` functions are exposed to Swift as `async` automatically (Swift bridges
  ObjC completion-handler methods to `async/await`), so no extra interop tooling is required
  for this skeleton. **SKIE** (Touchlab) can be added later for nicer `Flow`/sealed-class interop.
- Money fields are fixed-2dp **strings** end-to-end (e.g. `"70.00"`) to avoid float drift;
  parse to a decimal type in the UI if you need arithmetic.
