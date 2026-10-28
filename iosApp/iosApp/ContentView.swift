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
    /// A tapped support push publishes its ticket here (may already be set when MainView first appears).
    @ObservedObject private var push = PushRegistrar.shared
    @State private var selection = 0
    @State private var showingProfile = false
    @State private var showingNotifications = false
    /// The Ask the committee sheet, carrying the ticket to open (from a notification tap). An item,
    /// not a Bool + separate id: `.sheet(isPresented:)` built its content with a stale id.
    @State private var supportSheet: SupportSheet?
    /// Bumped to make Home refetch its dashboard.
    @State private var homeRefresh = 0
    /// Waits for the notifications sheet to finish dismissing before the support sheet appears.
    @State private var pendingSupportTicket: Int64?

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack {
                HomeView(selection: $selection, refreshToken: homeRefresh, onOpenSupport: { id in supportSheet = SupportSheet(ticketId: id) })
                    .background(AmbientBackground())
                    // The date + "Hi, {name}" greeting lives in Home's content; the bar keeps only the chrome.
                    .navigationTitle("")
                    .navigationBarTitleDisplayMode(.inline)
                    .modifier(TopChrome(initials: pentInitials(session.avatarName), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(0)

            NavigationStack {
                BillsView()
                    .background(AmbientBackground())
                    .navigationTitle("Bills")
                    .modifier(TopChrome(initials: pentInitials(session.avatarName), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Bills", systemImage: "creditcard.fill") }
            .tag(1)

            NavigationStack {
                LunchView()
                    .background(AmbientBackground())
                    .navigationTitle("Lunch")
                    .modifier(TopChrome(initials: pentInitials(session.avatarName), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Lunch", systemImage: "fork.knife") }
            .tag(2)

            NavigationStack {
                ActivitiesView()
                    .background(AmbientBackground())
                    .navigationTitle("Activities")
                    .modifier(TopChrome(initials: pentInitials(session.avatarName), unread: session.unreadCount,
                                        onProfile: { showingProfile = true }, onBell: { showingNotifications = true }))
            }
            .tabItem { Label("Activities", systemImage: "calendar") }
            .tag(3)
        }
        .tint(Pent.accent)
        // `initial: true` consumes a tap that cold-launched the app, before this view existed.
        .onChange(of: push.pendingTicketId, initial: true) { _, id in
            guard let id else { return }
            push.pendingTicketId = nil
            openSupportFromPush(id)
        }
        .sheet(isPresented: $showingProfile, onDismiss: presentPendingSupport) { ProfileView().environmentObject(session) }
        // Only one sheet at a time: open the conversation once the bell sheet has gone.
        .sheet(isPresented: $showingNotifications, onDismiss: presentPendingSupport) {
            NotificationsView(onOpenSupportTicket: { id in
                pendingSupportTicket = id
                showingNotifications = false
            })
            .environmentObject(session)
        }
        // Replying in the sheet changes the Home card's waiting-reply count.
        .sheet(item: $supportSheet, onDismiss: { homeRefresh += 1; presentPendingSupport() }) { sheet in
            SupportListView(openTicketId: sheet.ticketId).environmentObject(session)
        }
    }

    /// Opens the support thread from a tapped push: Home tab, any open sheet closed first.
    private func openSupportFromPush(_ id: Int64) {
        selection = 0
        pendingSupportTicket = id
        if showingNotifications { showingNotifications = false }
        else if showingProfile { showingProfile = false }
        else if supportSheet != nil { supportSheet = nil }
        else { presentPendingSupport() }  // nothing open, so no onDismiss will fire
    }

    private func presentPendingSupport() {
        if let id = pendingSupportTicket {
            pendingSupportTicket = nil
            supportSheet = SupportSheet(ticketId: id)
        }
    }
}

/// One presentation of the Ask the committee sheet; a fresh id each time so it always re-presents.
struct SupportSheet: Identifiable {
    let id = UUID()
    let ticketId: Int64?
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
