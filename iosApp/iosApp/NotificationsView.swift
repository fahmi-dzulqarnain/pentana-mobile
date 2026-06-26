//
//  NotificationsView.swift
//  iosApp
//
//  Reached from the top-right bell. Lists the member's notifications and marks
//  them read on open (clearing the badge).
//

import Shared
import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var items: [NotificationDto] = []
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            List {
                if items.isEmpty && !isLoading {
                    Text("No notifications yet.").foregroundStyle(.secondary)
                }
                ForEach(items, id: \.id) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title).font(.headline)
                        if let body = item.body, !body.isEmpty {
                            Text(body).font(.subheadline).foregroundStyle(.secondary)
                        }
                        if let when = item.createdAt.flatMap(relativeDate) {
                            Text(when).font(.caption2).foregroundStyle(.tertiary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
            .overlay { if isLoading && items.isEmpty { ProgressView() } }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await load() }
        }
    }

    private func load() async {
        isLoading = true
        do {
            let page = try await session.notifications.notifications()
            items = page.data
            // Opening the list marks everything read and clears the bell badge.
            if page.unreadCount > 0 {
                await session.markNotificationsRead()
            }
        } catch {
            // Keep what we have.
        }
        isLoading = false
    }

    private func relativeDate(_ iso: String) -> String? {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime]
        guard let date = parser.date(from: iso) else { return nil }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
