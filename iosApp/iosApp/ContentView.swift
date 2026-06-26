//
//  ContentView.swift
//  iosApp
//
//  Root switcher + the signed-in shell: glass TabView, ambient backgrounds,
//  and the floating profile (leading) + notifications bell (trailing) chrome.
//

import Shared
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        Group {
            if session.isBootstrapping {
                ZStack { AmbientBackground(); ProgressView() }
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
                    .background(AmbientBackground())
                    .navigationTitle("Hi, \(firstName)")
                    .modifier(TopChrome(initials: pentInitials(session.user?.name), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(0)

            NavigationStack {
                BillsView()
                    .background(AmbientBackground())
                    .navigationTitle("Bills")
                    .modifier(TopChrome(initials: pentInitials(session.user?.name), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Bills", systemImage: "creditcard.fill") }
            .tag(1)

            NavigationStack {
                LunchView()
                    .background(AmbientBackground())
                    .navigationTitle("Lunch")
                    .modifier(TopChrome(initials: pentInitials(session.user?.name), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Lunch", systemImage: "fork.knife") }
            .tag(2)

            NavigationStack {
                ActivitiesView()
                    .background(AmbientBackground())
                    .navigationTitle("Activities")
                    .modifier(TopChrome(initials: pentInitials(session.user?.name), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Activities", systemImage: "calendar") }
            .tag(3)
        }
        .tint(Pent.accent)
        .sheet(isPresented: $showingProfile) { ProfileView().environmentObject(session) }
        .sheet(isPresented: $showingNotifications) { NotificationsView().environmentObject(session) }
    }

    private var firstName: String {
        session.user?.name.split(separator: " ").first.map(String.init) ?? "there"
    }
}

// MARK: - Top chrome (avatar + bell)

struct TopChrome: ViewModifier {
    let initials: String
    let unread: Int
    let onProfile: () -> Void
    let onBell: () -> Void

    func body(content: Content) -> some View {
        content.toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onProfile) { AvatarInitials(initials: initials, size: 30) }
                    .accessibilityLabel("Profile")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onBell) { BellButton(count: unread) }
                    .accessibilityLabel(unread > 0 ? "Notifications, \(unread) unread" : "Notifications")
            }
        }
    }
}

struct BellButton: View {
    let count: Int
    var body: some View {
        Image(systemName: "bell.fill")
            .foregroundStyle(Pent.label)
            .overlay(alignment: .topTrailing) {
                if count > 0 {
                    Text(count > 9 ? "9+" : "\(count)")
                        .font(.system(size: 11, weight: .bold, design: .rounded)).monospacedDigit()
                        .foregroundStyle(.white)
                        .padding(.horizontal, 4)
                        .frame(minHeight: 16)
                        .frame(minWidth: 16)
                        .background(Pent.bad, in: Capsule())
                        .overlay(Capsule().strokeBorder(Pent.bgBase, lineWidth: 1.5))
                        .offset(x: 10, y: -9)
                }
            }
    }
}
