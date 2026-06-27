# Mobile Passkeys — Design & Deploy Spec

**Date:** 2026-06-27
**Goal:** Let members register a passkey on their phone and sign in with it, reusing the
existing `spatie/laravel-passkeys` setup but over JSON APIs for the mobile app.

## Identities (canonical values)
- **Relying-party ID:** `pentana.silentmode.net`
- **App bundle id:** `my.silentmode.pentana.iosApp`
- **Apple Team ID:** `7778Y2522V`
- **Associated Domain entitlement:** `webcredentials:pentana.silentmode.net`

## Prerequisites / deploy checklist (passkeys are inert until ALL are true)
1. `pentana.silentmode.net` serves the Laravel app over **HTTPS** (Cloudflare Tunnel).
2. `APP_URL=https://pentana.silentmode.net` in production (so the spatie RP id resolves to the host).
3. `https://pentana.silentmode.net/.well-known/apple-app-site-association` returns, as
   `application/json` (no extension, no redirect):
   ```json
   { "webcredentials": { "apps": ["7778Y2522V.my.silentmode.pentana.iosApp"] } }
   ```
4. The iOS app ships with the `webcredentials:pentana.silentmode.net` entitlement (real
   provisioning profile / signed build — the entitlement needs the paid Apple Developer team).
5. The app's API base for passkey calls is the **live domain** (the OS sets the assertion
   origin to `https://pentana.silentmode.net`; the server validates against that RP id). The
   LAN-IP dev base cannot be used for the passkey ceremony.

> Until 1–5 hold, the code builds and the non-passkey app keeps working on the LAN IP, but the
> passkey buttons will fail at the OS step. This is expected.

## Backend (`pentana-system`)
- **AASA:** `public/.well-known/apple-app-site-association` (static JSON above).
- **`App\Http\Controllers\Api\V1\PasskeyController`**, routes under `/api/v1/passkeys`:
  | Route | Auth | Returns |
  |-------|------|---------|
  | `POST login/options` | public, `throttle:10,1` | `{ state, publicKey }` (discoverable / usernameless) |
  | `POST login/verify` | public, `throttle:10,1` | `{ token, user }` on success |
  | `POST register/options` | `auth:sanctum` | `{ state, publicKey }` |
  | `POST register/verify` | `auth:sanctum` | `{ id, name, last_used_at }` |
  | `GET passkeys` | `auth:sanctum` | list of the member's passkeys |
  | `DELETE passkeys/{id}` | `auth:sanctum` | 204 |
- **Challenge state:** `options` serialises the WebAuthn options into `Cache` for 5 min keyed by
  a random `state` token returned to the client; `verify` requires the same `state` and pulls
  the stored challenge/options to validate against. Stateless — no session.
- **Verification:** reuse spatie's configured actions
  (`GeneratePasskey{Register,Authentication}OptionsAction`, `FindPasskeyToAuthenticateAction`,
  the `LoggingStorePasskeyAction`) and webauthn-lib validators driven by the existing
  `LocalAwareCeremonyStepManagerFactoryAction`. Login is **usernameless** (empty
  `allowCredentials`; the user handle in the assertion resolves the member).
- **Tests:** response shapes, auth gating, and the `state` round-trip (full WebAuthn crypto is
  verified on-device, since unit-testing it needs a virtual authenticator).

## Shared (KMP)
- **`PasskeyRepository`** (HTTP only): `loginOptions()`, `loginVerify(state, credentialJson)`,
  `registerOptions()`, `registerVerify(state, credentialJson)`, `list()`, `delete(id)`.
  WebAuthn options/credentials cross the boundary as JSON strings — Swift parses the options to
  build the OS request and serialises the OS result back. MockEngine tests for the plumbing.

## iOS (`pentana-mobile`)
- **`iosApp.entitlements`** with `com.apple.developer.associated-domains =
  ["webcredentials:pentana.silentmode.net"]`; `CODE_SIGN_ENTITLEMENTS` wired in the project.
- **`AppConfig.passkeyRelyingParty = "pentana.silentmode.net"`**.
- **`PasskeyManager`** (Swift): wraps `ASAuthorizationPlatformPublicKeyCredentialProvider`
  with async `register(optionsJson)` / `signIn(optionsJson)` (continuation + delegate +
  presentation-context provider). The only non-shared part.
- **`SessionStore`**: `passkeySignIn()` and `registerPasskey()` orchestrate
  options → `PasskeyManager` → verify.
- **LoginView**: "Sign in with passkey" button under the form.
- **ProfileView**: a "Passkeys" section — "Set up a passkey", list, remove.

## Data flow (sign-in)
tap → `loginOptions()` → `{state, optionsJson}` → Swift builds assertion request → Face ID →
assertion → `loginVerify(state, credentialJson)` → `{token, user}` → signed in.
Register mirrors it (authenticated; `createCredentialRegistrationRequest`).

## Error handling
OS cancel → silent return to the prior screen; no credential found → friendly message;
verify/network failure → message. Login endpoints throttled.

## Build order
1. Backend: AASA + endpoints + tests.
2. KMP: `PasskeyRepository` + tests.
3. iOS: entitlement, `PasskeyManager`, `AppConfig`, `SessionStore`, Login + Profile wiring.
