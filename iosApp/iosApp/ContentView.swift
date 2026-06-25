//
//  ContentView.swift
//  iosApp
//
//  Root switcher: bootstraps the session, then shows Login or the signed-in app.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        Group {
            if session.isBootstrapping {
                ProgressView("Loading…")
            } else if session.isLoggedIn {
                MainView()
            } else {
                LoginView()
            }
        }
        .task { await session.bootstrap() }
    }
}

struct MainView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        NavigationStack {
            BillsView()
                .navigationTitle("My Bills")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Sign out") { Task { await session.logout() } }
                    }
                }
        }
    }
}
