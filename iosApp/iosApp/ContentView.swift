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
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack {
                HomeView(selection: $selection)
                    .navigationTitle("Home")
                    .toolbar { signOut }
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(0)

            NavigationStack {
                BillsView()
                    .navigationTitle("My Bills")
                    .toolbar { signOut }
            }
            .tabItem { Label("Bills", systemImage: "creditcard") }
            .tag(1)

            NavigationStack {
                LunchView()
                    .navigationTitle("Lunch")
                    .toolbar { signOut }
            }
            .tabItem { Label("Lunch", systemImage: "fork.knife") }
            .tag(2)

            NavigationStack {
                ActivitiesView()
                    .navigationTitle("Activities")
                    .toolbar { signOut }
            }
            .tabItem { Label("Activities", systemImage: "calendar") }
            .tag(3)
        }
    }

    @ToolbarContentBuilder
    private var signOut: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button("Sign out") { Task { await session.logout() } }
        }
    }
}
