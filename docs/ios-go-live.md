# PENTANA iOS — Go-Live Checklist

Steps to take the iOS app from "builds locally" to "passkeys + push working against
production, ready for TestFlight." The **code** side is done (the app targets the live
domain); everything here is **Apple Developer portal / Xcode / server** work.

**Fixed values**

| | |
|---|---|
| Bundle ID | `my.silentmode.pentana` |
| Apple Team ID | `7778Y2522V` |
| Domain (relying party) | `pentana.silentmode.net` |
| API base URL | `https://pentana.silentmode.net/api/v1` |

## Already done ✅
- `AppConfig.baseURL` → production HTTPS (LAN dev URL kept as a commented fallback).
- `iosApp.entitlements` has `webcredentials:pentana.silentmode.net` + `aps-environment`.
- `passkeyRelyingParty` matches the domain.
- `https://pentana.silentmode.net/.well-known/apple-app-site-association` is live, `200`, correct JSON, no redirect.
- API is live over HTTPS; `POST /api/v1/passkeys/login/options` returns a valid challenge.
- `device_tokens` table migrated on Supabase.

---

## 1. Apple Developer portal — [developer.apple.com/account](https://developer.apple.com/account)

**a. App ID capabilities.** Identifiers → `my.silentmode.pentana` → Edit, enable:
- **Associated Domains** (powers passkeys)
- **Push Notifications**

then Save.

**b. APNs Auth Key (`.p8`).** Keys → ➕ → name it → tick **Apple Push Notifications service (APNs)** →
Continue → Register → **download the `.p8` once (it can't be re-downloaded)** and note the **Key ID**.
One key covers sandbox + production and never expires.

## 2. Xcode — target `iosApp` → Signing & Capabilities
- **Team** = the paid team (`7778Y2522V`); Automatic signing on.
- Confirm **Associated Domains** shows `webcredentials:pentana.silentmode.net` — this forces the
  provisioning profile to include it (the entitlement is already in the file).
- **+ Capability → Push Notifications**. Keep `aps-environment = development` for device debugging;
  Xcode switches it to `production` automatically when you Archive for TestFlight/App Store.

## 3. Server (production) — enable push
Put the `.p8` on the server (outside the web root, `chmod 600`, never committed), then set in `.env`:

```dotenv
APN_KEY_ID=<Key ID from step 1b>
APN_TEAM_ID=7778Y2522V
APN_BUNDLE_ID=my.silentmode.pentana
APN_PRIVATE_KEY_PATH=storage/app/private/apns/AuthKey_XXXXXXXXXX.p8
APN_PRODUCTION=false   # device/debug builds = sandbox; set true for TestFlight/App Store
```

then `php artisan config:cache`. See `pentana-system/DEPLOYMENT.md` step 2i for the full detail.
`APP_URL` is already `https://pentana.silentmode.net`.

> **APNs environment gotcha:** device (debug) builds use the **sandbox** → `APN_PRODUCTION=false`.
> **TestFlight / App Store** builds use **production** → set `true`. A mismatch fails silently
> with `BadDeviceToken`.

## 4. Verify on a real device (signed into iCloud)
- **App loads:** login → dashboard / bills / lunch / activities now hit production.
- **Passkeys:** sign in → Profile → **Set up a passkey** (Face ID) → sign out → **Sign in with a
  passkey**. Requires the device on iCloud Keychain.
- **Push:** allow notifications on first launch → confirm a row appears in `device_tokens` →
  publish an activity in the admin → a banner arrives.

## 5. (When ready) TestFlight
Archive → upload to App Store Connect → TestFlight, and set `APN_PRODUCTION=true` on the server.

---

## Notes
- **Passkeys and push are independent.** Passkeys need Associated Domains + the AASA (done) +
  correct provisioning. Push needs the Push capability + the `.p8` + `APN_*` env. Neither depends
  on the other.
- **Every build now hits production** (including debug from Xcode → live Supabase). For offline
  local development, uncomment the LAN URL in `AppConfig.swift` and run
  `php artisan serve --host=0.0.0.0 --port=8000` — but note passkeys only work against the live
  HTTPS domain, never a LAN IP.
