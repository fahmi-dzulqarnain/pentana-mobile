//
//  iosAppApp.swift
//  iosApp
//

import SwiftUI

@main
struct iosAppApp: App {
    @StateObject private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
        }
    }
}
