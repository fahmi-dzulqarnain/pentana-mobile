# Push Notifications — Design & Deploy Spec

**Date:** 2026-06-27
**Goal:** Deliver the member-facing notifications (activities + lunch) as APNs pushes, in
addition to the existing in-app bell + email — reusing Laravel notifications.

## Identities
- **APNs topic / bundle id:** `my.silentmode.pentana`
- **Apple Team ID:** `7778Y2522V`
- **Auth:** token-based APNs (`.p8` Auth Key) — Key ID + Team ID + key file.

## Scope (triggers)
Push mirrors the in-app bell — every notification already going to `database` also gets `apn`:
`ActivityPublished`, `ActivityCancelled`, `ActivityReminder`, `WaitlistPromoted`, `LunchPublished`.
`BirthdayWish` stays mail-only. Sent **inline** (no queue worker), like the rest.

## Backend (`pentana-system`)
- Package: `laravel-notification-channels/apn` (reads `config('broadcasting.connections.apn')`).
- **Config** `broadcasting.connections.apn`: `key_id`, `team_id`, `app_bundle_id`,
  `private_key_path`, `private_key_secret`, `production` — all from `APN_*` env. The `.p8`
  lives outside the repo (path via env); never committed.
- **`device_tokens`** table (`user_id` FK cascade, `token` unique, `platform` default `ios`,
  timestamps) + `DeviceToken` model.
- **User**: `deviceTokens()` hasMany + `routeNotificationForApn()` returning the token strings.
- **API** (auth): `POST /api/v1/device-tokens` `{token, platform}` — upsert; **reassigns** a
  token to the current user if it was registered elsewhere. `DELETE /api/v1/device-tokens/{token}`.
- **Notifications**: add `toApn()` (reusing each notification's title/body) and add the `apn`
  channel to `via()` **only when** APNs is configured *and* the member has a token — so dev/tests
  without `APN_*` are unaffected and never hit APNs.
- **Tests**: device-token register (upsert + reassign), delete, auth/scoping; and that a
  notification's `via()` includes the APN channel + `toApn()` builds the right title/body when
  configured. Real delivery is device-only.

## Shared (KMP)
- `DeviceTokensRepository.register(token, platform)` / `unregister(token)` (HTTP only) +
  MockEngine tests. APNs registration itself is Swift.

## iOS (`pentana-mobile`)
- **Push Notifications** capability → `aps-environment` in `iosApp.entitlements`.
- `AppDelegate` via `UIApplicationDelegateAdaptor`:
  - after login, request `UNUserNotificationCenter` authorization (alert/badge/sound) → on grant,
    `registerForRemoteNotifications()`;
  - `didRegisterForRemoteNotificationsWithDeviceToken` → hex-encode → `register` via the repo
    (only when signed in);
  - foreground `willPresent` → show banner; `didReceive` (tap) → refresh the bell (deep-link later).
- Logout → `unregister` the current token.

## Data flow
launch (signed in) → permission → APNs device token → `POST /device-tokens`.
backend event → notification (`apn` channel) → APNs → banner on device.

## Deploy prerequisites (the `.p8`)
1. Enable **Push Notifications** capability on the App ID `my.silentmode.pentana`.
2. Generate an **APNs Auth Key (.p8)** → note the **Key ID**.
3. Server env: `APN_KEY_ID`, `APN_TEAM_ID=7778Y2522V`, `APN_BUNDLE_ID=my.silentmode.pentana`,
   `APN_PRIVATE_KEY_PATH` (path to the `.p8` on the server), `APN_PRIVATE_KEY_SECRET` (usually
   empty), `APN_PRODUCTION` (`false` for dev/TestFlight sandbox builds, `true` for App Store).
4. Run the `device_tokens` migration on Supabase.
5. Real delivery needs a **physical device**; the simulator can be exercised with a local
   `xcrun simctl push <udid> my.silentmode.pentana payload.apns`.

## Build order
1. Backend: config → table → endpoint → channel → tests.
2. KMP: `DeviceTokensRepository` + tests.
3. iOS: entitlement, `AppDelegate`, permission, token POST.
