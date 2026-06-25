//
//  SessionStore.swift
//  iosApp
//
//  App-wide DI + auth state. Wires the shared ApiClient + repositories and tracks
//  the signed-in member.
//

import Foundation
import Shared

@MainActor
final class SessionStore: ObservableObject {
    @Published var user: UserDto?
    @Published var isBootstrapping = true
    @Published var errorMessage: String?

    let auth: AuthRepository
    let bills: BillsRepository

    init() {
        let client = ApiClient(baseUrl: AppConfig.baseURL, tokenStore: KeychainTokenStore(), engine: nil)
        auth = AuthRepository(client: client)
        bills = BillsRepository(client: client)
    }

    var isLoggedIn: Bool { user != nil }

    /// On launch: if a token exists, fetch the profile; drop it if the token is stale.
    func bootstrap() async {
        if auth.isLoggedIn() {
            do {
                user = try await auth.me()
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
        } catch {
            errorMessage = "Sign in failed. Check your email and password."
        }
    }

    func logout() async {
        try? await auth.logout()
        user = nil
    }
}
