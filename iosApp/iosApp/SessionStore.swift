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

    init() {
        let client = ApiClient(baseUrl: AppConfig.baseURL, tokenStore: KeychainTokenStore(), engine: nil)
        auth = AuthRepository(client: client)
        bills = BillsRepository(client: client)
        lunch = LunchRepository(client: client)
        activities = ActivitiesRepository(client: client)
        dashboard = DashboardRepository(client: client)
        notifications = NotificationsRepository(client: client)
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
}
