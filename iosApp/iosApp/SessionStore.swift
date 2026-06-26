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

    private let tokenStore = KeychainTokenStore()
    private let passkeyManager = PasskeyManager(relyingParty: AppConfig.passkeyRelyingParty)

    init() {
        let client = ApiClient(baseUrl: AppConfig.baseURL, tokenStore: tokenStore, engine: nil)
        auth = AuthRepository(client: client)
        bills = BillsRepository(client: client)
        lunch = LunchRepository(client: client)
        activities = ActivitiesRepository(client: client)
        dashboard = DashboardRepository(client: client)
        notifications = NotificationsRepository(client: client)
        passkey = PasskeyRepository(client: client)
    }

    var isLoggedIn: Bool { user != nil }

    /// On launch: if a token exists, fetch the profile; drop it if the token is stale.
    func bootstrap() async {
        if auth.isLoggedIn() {
            do {
                user = try await auth.me()
                await refreshBadge()
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
        } catch {
            // Surface the real reason (connection / ATS / 401 / parsing) instead of guessing.
            errorMessage = "Sign in failed: \(error.localizedDescription)"
        }
    }

    func logout() async {
        try? await auth.logout()
        user = nil
        unreadCount = 0
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
