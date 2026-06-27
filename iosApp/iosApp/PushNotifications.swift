//
//  PushNotifications.swift
//  iosApp
//
//  Bridges UIApplication's remote-notification callbacks (AppDelegate) to the
//  SwiftUI SessionStore via a small shared registrar.
//

import UIKit
import UserNotifications

/// Shared hand-off between the AppDelegate (UIKit) and SessionStore (SwiftUI).
final class PushRegistrar {
    static let shared = PushRegistrar()
    private init() {}

    /// Most recent APNs device token (hex), if one has already arrived.
    private(set) var deviceToken: String?
    /// Set by SessionStore — invoked when a device token is received.
    var onToken: ((String) -> Void)?
    /// Set by SessionStore — invoked when a push is presented/tapped.
    var onReceive: (() -> Void)?

    func handle(token: String) {
        deviceToken = token
        onToken?(token)
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        PushRegistrar.shared.handle(token: hex)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Non-fatal — the member can enable notifications later from Settings.
    }

    // Show the banner while the app is in the foreground.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        PushRegistrar.shared.onReceive?()
        return [.banner, .badge, .sound]
    }

    // Tap on a delivered notification.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        PushRegistrar.shared.onReceive?()
    }
}
