//
//  AppConfig.swift
//  iosApp
//

import Foundation

enum AppConfig {
    /// Base URL of the PENTANA member API.
    ///
    /// Points at the Mac's LAN IP so both the iOS Simulator and a physical device
    /// (on the same Wi-Fi) can reach the dev server — run it with
    /// `php artisan serve --host=0.0.0.0 --port=8000`. The ATS exception in
    /// Info.plist (NSAllowsLocalNetworking) allows the plain-HTTP connection.
    ///
    /// Update the IP if your network changes (`ipconfig getifaddr en0`), or swap
    /// in the deployed Cloudflare HTTPS URL for a real build.
    static let baseURL = "http://192.168.0.177:8000/api/v1"

    /// Passkey relying-party ID. Must match the `webcredentials:` Associated Domain
    /// and the server's APP_URL host. Passkeys only work against the live HTTPS
    /// domain (the OS derives the assertion origin from this) — not the LAN IP.
    static let passkeyRelyingParty = "pentana.silentmode.my"
}
