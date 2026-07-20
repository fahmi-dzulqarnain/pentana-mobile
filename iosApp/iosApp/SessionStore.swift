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

    /// Shared session state machine (bootstrap/login/logout/badge). DI + APNs + passkeys stay
    /// native here; the manager's flows are bridged into the @Published properties below.
    private let manager: SessionManager

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
        manager = SessionManager(auth: auth, notifications: notifications)

        // Forward APNs device tokens / foreground pushes from the AppDelegate.
        PushRegistrar.shared.onToken = { [weak self] token in
            Task { await self?.registerDeviceToken(token) }
        }
        PushRegistrar.shared.onReceive = { [weak self] in
            Task { await self?.refreshBadge() }
        }

        // Bridge the shared manager's flows into the @Published properties the views observe.
        Task { [weak self] in
            guard let self else { return }
            for await sessionState in manager.state {
                self.user = sessionState.user
                self.unreadCount = Int(sessionState.unread)
                self.isBootstrapping = sessionState.bootstrapping
            }
        }
        Task { [weak self] in
            guard let self else { return }
            for await message in manager.loginError {
                self.errorMessage = message
            }
        }
    }

    var isLoggedIn: Bool { user != nil }

    /// Vend a shared Lunch presentation store, backed by the shared `LunchRepository`.
    /// The view holds it for its lifetime and reuses it across reappearance (it is not
    /// cleared on disappear); it releases with the view.
    func makeLunchStore() -> LunchStore { LunchStore(repo: lunch) }

    /// Vend a shared Home presentation store, backed by the shared `DashboardRepository`.
    /// Same lifecycle contract as makeLunchStore(): held in @State, reused across reappear, not cleared.
    func makeHomeStore() -> HomeStore { HomeStore(repo: dashboard) }

    /// Vend a shared Notifications presentation store for the bell sheet.
    func makeNotificationsStore() -> NotificationsStore { NotificationsStore(repo: notifications) }

    /// Vend a shared Bills presentation store. Same lifecycle contract as the other factories:
    /// held in @State, reused across reappear, not cleared.
    func makeBillsStore() -> BillsStore { BillsStore(repo: bills) }

    /// Vend a shared Activities presentation store. Same lifecycle contract as the other factories:
    /// held in @State, reused across reappear, not cleared.
    func makeActivitiesStore() -> ActivitiesStore { ActivitiesStore(repo: activities) }

    /// On launch: shared bootstrap (token → profile → badge), then native push enablement.
    func bootstrap() async {
        try? await manager.bootstrap()
        if manager.state.value.user != nil {
            await enablePushNotifications()
        }
    }

    func login(email: String, password: String) async {
        // Flatten the double optional (`try?` over an async function returning UserDto? yields
        // UserDto?? — a failed login would otherwise read as .some(nil) != nil).
        let loggedIn = (try? await manager.login(email: email, password: password, deviceName: "iOS")) ?? nil
        if loggedIn != nil {
            await enablePushNotifications()
        }
    }

    func logout() async {
        if let token = lastDeviceToken { try? await deviceTokens.unregister(token: token) }
        try? await manager.logout()
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

    /// Refresh the bell badge's unread count (best-effort; the manager no-ops when logged out).
    func refreshBadge() async {
        try? await manager.refreshBadge()
    }

    /// Mark every notification read and clear the badge.
    func markNotificationsRead() async {
        try? await manager.markAllRead()
    }

    // MARK: - Passkeys

    /// Sign in with a passkey: fetch options → OS ceremony → verify → session.
    func passkeySignIn() async {
        errorMessage = nil
        do {
            let challenge = try await passkey.loginOptions()
            let credential = try await passkeyManager.signIn(optionsJson: challenge.publicKeyJson)
            let verifiedUser = try await passkey.loginVerify(state: challenge.state, credentialJson: credential)
            manager.onLoggedIn(user: verifiedUser)
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
