//
//  SessionStore.swift
//  iosApp
//
//  App-wide DI + auth state. Wires the shared ApiClient + repositories and tracks
//  the signed-in member.
//

import Combine
import Foundation
import Shared
import UIKit
import UserNotifications

@MainActor
final class SessionStore: ObservableObject {
    @Published var user: UserDto?
    @Published var isBootstrapping = true
    @Published var errorMessage: String?
    @Published var unreadCount = 0

    let auth: AuthRepository
    let bills: BillsRepository
    let lunch: LunchRepository
    let activities: ActivitiesRepository
    let dashboard: DashboardRepository
    let notifications: NotificationsRepository
    let passkey: PasskeyRepository
    let deviceTokens: DeviceTokensRepository

    private let tokenStore = KeychainTokenStore()
    private let passkeyManager = PasskeyManager(relyingParty: AppConfig.passkeyRelyingParty)
    private var lastDeviceToken: String?

    init() {
        let client = ApiClient(baseUrl: AppConfig.baseURL, tokenStore: tokenStore, engine: nil)
        auth = AuthRepository(client: client)
        bills = BillsRepository(client: client)
        lunch = LunchRepository(client: client)
        activities = ActivitiesRepository(client: client)
        dashboard = DashboardRepository(client: client)
        notifications = NotificationsRepository(client: client)
        passkey = PasskeyRepository(client: client)
        deviceTokens = DeviceTokensRepository(client: client)

        // Forward APNs device tokens / foreground pushes from the AppDelegate.
        PushRegistrar.shared.onToken = { [weak self] token in
            Task { await self?.registerDeviceToken(token) }
        }
        PushRegistrar.shared.onReceive = { [weak self] in
            Task { await self?.refreshBadge() }
        }
    }

    var isLoggedIn: Bool { user != nil }

    /// Vend a fresh shared Lunch presentation store, backed by the shared `LunchRepository`.
    /// The view owns its lifecycle (`clear()` on disappear).
    func makeLunchStore() -> LunchStore { LunchStore(repo: lunch) }

    /// On launch: if a token exists, fetch the profile; drop it if the token is stale.
    func bootstrap() async {
        if auth.isLoggedIn() {
            do {
                user = try await auth.me()
                await refreshBadge()
                await enablePushNotifications()
            } catch {
                try? await auth.logout()
                user = nil
            }
        }
        isBootstrapping = false
    }

    func login(email: String, password: String) async {
        errorMessage = nil
        do {
            user = try await auth.login(email: email, password: password, deviceName: "iOS")
            await refreshBadge()
            await enablePushNotifications()
        } catch {
            // Surface the real reason (connection / ATS / 401 / parsing) instead of guessing.
            errorMessage = "Sign in failed: \(error.localizedDescription)"
        }
    }

    func logout() async {
        if let token = lastDeviceToken { try? await deviceTokens.unregister(token: token) }
        try? await auth.logout()
        user = nil
        unreadCount = 0
    }

    // MARK: - Push notifications

    /// Ask for notification permission, then register for remote notifications. Safe to call
    /// repeatedly — iOS only prompts once. Registers any token already captured by the AppDelegate.
    func enablePushNotifications() async {
        guard isLoggedIn else { return }
        let granted = (try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        guard granted else { return }
        UIApplication.shared.registerForRemoteNotifications()
        if let token = PushRegistrar.shared.deviceToken { await registerDeviceToken(token) }
    }

    private func registerDeviceToken(_ token: String) async {
        guard isLoggedIn else { return }
        lastDeviceToken = token
        do { try await deviceTokens.register(token: token, platform: "ios") } catch {}
    }

    /// Refresh the bell badge's unread count (best-effort).
    func refreshBadge() async {
        guard isLoggedIn else { return }
        do { unreadCount = Int(try await notifications.notifications().unreadCount) } catch {}
    }

    /// Mark every notification read and clear the badge.
    func markNotificationsRead() async {
        do { _ = try await notifications.markAllRead() } catch {}
        unreadCount = 0
    }

    // MARK: - Passkeys

    /// Sign in with a passkey: fetch options → OS ceremony → verify → session.
    func passkeySignIn() async {
        errorMessage = nil
        do {
            let challenge = try await passkey.loginOptions()
            let credential = try await passkeyManager.signIn(optionsJson: challenge.publicKeyJson)
            user = try await passkey.loginVerify(state: challenge.state, credentialJson: credential)
            await refreshBadge()
            await enablePushNotifications()
        } catch PasskeyManager.PasskeyError.canceled {
            // User dismissed the sheet — no error.
        } catch {
            errorMessage = "Couldn't sign in with a passkey. Please try again or use your password."
        }
    }

    /// Register a passkey on this device for the signed-in member.
    @discardableResult
    func registerPasskey(name: String) async -> Bool {
        do {
            let challenge = try await passkey.registerOptions()
            let credential = try await passkeyManager.register(optionsJson: challenge.publicKeyJson)
            try await passkey.registerVerify(state: challenge.state, credentialJson: credential, name: name)
            return true
        } catch {
            return false
        }
    }
}
