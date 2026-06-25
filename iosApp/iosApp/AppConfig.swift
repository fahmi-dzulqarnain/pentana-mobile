//
//  AppConfig.swift
//  iosApp
//

import Foundation

enum AppConfig {
    /// Base URL of the PENTANA member API.
    /// - iOS Simulator can reach the Mac via `localhost`.
    /// - A physical device needs the Mac's LAN IP (e.g. http://192.168.x.x:8000/api/v1)
    ///   or the deployed Cloudflare HTTPS URL.
    static let baseURL = "http://localhost:8000/api/v1"
}
