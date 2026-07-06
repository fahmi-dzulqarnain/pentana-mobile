//
//  AppConfig.swift
//  iosApp
//

import Foundation

enum AppConfig {
    /// Base URL of the PENTANA member API — the live production domain.
    ///
    /// Passkeys/WebAuthn require this HTTPS host: the OS derives the assertion origin
    /// from it and validates it against the `webcredentials:` Associated Domain +
    /// `/.well-known/apple-app-site-association`. A LAN IP cannot be used for passkeys.
    ///
    /// For offline local development against `php artisan serve --host=0.0.0.0 --port=8000`,
    /// temporarily swap in your Mac's LAN IP (the NSAllowsLocalNetworking ATS exception in
    /// Info.plist permits the plain-HTTP connection):
    ///     static let baseURL = "http://192.168.0.177:8000/api/v1"
    static let baseURL = "https://pentana.silentmode.net/api/v1"

    /// Passkey relying-party ID. Must match the `webcredentials:` Associated Domain
    /// and the server's APP_URL host. Passkeys only work against the live HTTPS
    /// domain (the OS derives the assertion origin from this) — not the LAN IP.
    static let passkeyRelyingParty = "pentana.silentmode.net"
}
