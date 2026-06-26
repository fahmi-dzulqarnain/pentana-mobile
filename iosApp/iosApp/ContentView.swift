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
    @State private var showingProfile = false
    @State private var showingNotifications = false

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack {
                HomeView(selection: $selection)
                    .navigationTitle("Home")
                    .toolbar { topBar }
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(0)

            NavigationStack {
                BillsView()
                    .navigationTitle("My Bills")
                    .toolbar { topBar }
            }
            .tabItem { Label("Bills", systemImage: "creditcard") }
            .tag(1)

            NavigationStack {
                LunchView()
                    .navigationTitle("Lunch")
                    .toolbar { topBar }
            }
            .tabItem { Label("Lunch", systemImage: "fork.knife") }
            .tag(2)

            NavigationStack {
                ActivitiesView()
                    .navigationTitle("Activities")
                    .toolbar { topBar }
            }
            .tabItem { Label("Activities", systemImage: "calendar") }
            .tag(3)
        }
        .sheet(isPresented: $showingProfile) {
            ProfileView().environmentObject(session)
        }
        .sheet(isPresented: $showingNotifications) {
            NotificationsView().environmentObject(session)
        }
    }

    // Profile (account + sign out) on the left; notifications bell with unread badge on the right.
    @ToolbarContentBuilder
    private var topBar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button { showingProfile = true } label: {
                Image(systemName: "person.crop.circle")
            }
            .accessibilityLabel("Profile")
        }
        ToolbarItem(placement: .topBarTrailing) {
            Button { showingNotifications = true } label: {
                Image(systemName: session.unreadCount > 0 ? "bell.badge.fill" : "bell")
                    .symbolRenderingMode(session.unreadCount > 0 ? .multicolor : .monochrome)
            }
            .accessibilityLabel("Notifications")
        }
    }
}
