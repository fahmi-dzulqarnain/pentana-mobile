//
//  iosAppApp.swift
//  iosApp
//

import SwiftUI

@main
struct iosAppApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var session = SessionStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
                // Notifications may have been turned on or off in Settings while we were away.
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { Task { await session.syncPushRegistration() } }
                }
        }
    }
}
