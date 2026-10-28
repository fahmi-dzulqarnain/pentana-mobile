//
//  PushNotifications.swift
//  iosApp
//
//  Bridges UIApplication's remote-notification callbacks (AppDelegate) to the
//  SwiftUI SessionStore via a small shared registrar.
//

import Combine
import UIKit
import UserNotifications

/// Shared hand-off between the AppDelegate (UIKit) and SessionStore (SwiftUI).
final class PushRegistrar: ObservableObject {
    static let shared = PushRegistrar()
    private init() {}

    /// Most recent APNs device token (hex), if one has already arrived.
    private(set) var deviceToken: String?
    /// Set by SessionStore - invoked when a device token is received.
    var onToken: ((String) -> Void)?
    /// Set by SessionStore - invoked when a push is presented/tapped.
    var onReceive: (() -> Void)?
    /// Support ticket from a tapped support push. Stays set until MainView consumes it, so a tap that
    /// cold-launches the app (before MainView exists, or while still signing in) isn't lost.
    @Published var pendingTicketId: Int64?

    /// Reads the backend's top-level `ticket_id` from a push payload. It arrives as an NSNumber/Int
    /// through APNs, but accept a numeric string too; anything else (non-support pushes) is nil.
    static func ticketId(from userInfo: [AnyHashable: Any]) -> Int64? {
        let raw = userInfo["ticket_id"]
        // A Bool bridges to NSNumber (and would cast to Int); it is not a ticket id.
        if let n = raw as? NSNumber, CFGetTypeID(n) == CFBooleanGetTypeID() { return nil }
        switch raw {
        case let n as Int64: return n
        case let n as Int: return Int64(n)
        case let n as NSNumber: return n.int64Value
        case let s as String: return Int64(s.trimmingCharacters(in: .whitespaces))
        default: return nil
        }
    }

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
        // Non-fatal - the member can enable notifications later from Settings.
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
        if let id = PushRegistrar.ticketId(from: response.notification.request.content.userInfo) {
            await MainActor.run { PushRegistrar.shared.pendingTicketId = id }
        }
    }
}
