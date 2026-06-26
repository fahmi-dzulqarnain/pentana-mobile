//
//  NotificationsView.swift
//  iosApp
//
//  Notifications sheet from the bell. Marks read on open (clears the badge).
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
            ScrollView {
                VStack(spacing: 0) {
                    if items.isEmpty && !isLoading {
                        EmptyStateView(symbol: "bell.fill", title: "No notifications yet",
                                       message: "Lunch, activity and payment updates will show up here.")
                            .containerRelativeFrame(.vertical, alignment: .center)
                    } else {
                        HStack {
                            Spacer()
                            Button("Mark all read") { Task { await session.markNotificationsRead() } }
                                .font(.pentFoot).tint(Pent.accent)
                        }
                        .padding(.bottom, 6)

                        InsetGroup {
                            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                                NotifRow(item: item)
                                if index < items.count - 1 { PentHairline(leadingInset: 60) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
            .overlay { if isLoading && items.isEmpty { ProgressView() } }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.tint(Pent.accent)
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
            if page.unreadCount > 0 { await session.markNotificationsRead() }
        } catch {}
        isLoading = false
    }
}

private struct NotifRow: View {
    let item: NotificationDto

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack(alignment: .topLeading) {
                DomainIcon(symbol: glyph.0, tint: glyph.1, bg: glyph.2, size: 36, corner: 10, iconSize: 18)
                if !item.read {
                    Circle().fill(Pent.accentSolid).frame(width: 9, height: 9)
                        .overlay(Circle().strokeBorder(Pent.surface, lineWidth: 1.5))
                        .offset(x: -3, y: -2)
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .top, spacing: 8) {
                    Text(item.title).font(.pentBody).fontWeight(item.read ? .medium : .semibold).foregroundStyle(Pent.label)
                    Spacer(minLength: 4)
                    if let t = item.createdAt.flatMap(PentDates.relative) {
                        Text(t).font(.pentCap).foregroundStyle(Pent.label3).fixedSize()
                    }
                }
                if let body = item.body, !body.isEmpty {
                    Text(body).font(.pentFoot).foregroundStyle(Pent.label2)
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 13)
        .background(item.read ? Color.clear : Pent.accentSolid.opacity(0.07))
    }

    // Derive a domain glyph from the title keywords (no type on the DTO).
    private var glyph: (String, Color, Color) {
        let t = item.title.lowercased()
        if t.contains("lunch") { return ("fork.knife", Pent.lunch, Pent.lunchBg) }
        if t.contains("cancel") { return ("xmark.circle.fill", Pent.bad, Pent.badBg) }
        if t.contains("proof") || t.contains("payment") || t.contains("dues") { return ("doc.text.fill", Pent.proof, Pent.proofBg) }
        if t.contains("you're in") || t.contains("waitlist") { return ("party.popper.fill", Pent.activ, Pent.activBg) }
        if t.contains("activity") || t.contains("hik") || t.contains("clean") || t.contains("workshop") { return ("calendar", Pent.activ, Pent.activBg) }
        return ("bell.fill", Pent.neutral, Pent.neutralBg)
    }
}
