//
//  NotificationsView.swift
//  iosApp
//
//  Notifications sheet from the bell. Marks read on open (clears the badge).
//

@preconcurrency import Shared
import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var store: NotificationsStore?
    @State private var state: NotifUiState = NotifUiStateLoading.shared
    @State private var markedRead = false

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Notifications")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }.tint(Pent.accent)
                    }
                }
                .task {
                    let activeStore = store ?? session.makeNotificationsStore()
                    store = activeStore
                    async let states: Void = {
                        for await value in activeStore.state {
                            let shouldMark = await MainActor.run { () -> Bool in
                                state = value
                                // Opening the sheet with unread items marks everything read (badge lives
                                // in the session layer until the shared SessionManager lands).
                                guard case .content(let content) = onEnum(of: value),
                                      content.items.contains(where: { !$0.read }), !markedRead else { return false }
                                markedRead = true
                                return true
                            }
                            if shouldMark { await session.markNotificationsRead() }
                        }
                    }()
                    _ = await states
                }
        }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "bell.fill", title: "Couldn't load", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
        case .content(let content):
            ScrollView {
                VStack(spacing: 0) {
                    if content.items.isEmpty {
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
                            ForEach(Array(content.items.enumerated()), id: \.element.id) { index, item in
                                NotifRow(item: item)
                                if index < content.items.count - 1 { PentHairline(leadingInset: 60) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
        }
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
                    if let relativeTime = item.createdAt.flatMap(PentDates.relative) {
                        Text(relativeTime).font(.pentCap).foregroundStyle(Pent.label3).fixedSize()
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

    // Derive a domain glyph from the shared notification-kind classification.
    private var glyph: (String, Color, Color) {
        switch notificationKind(title: item.title) {
        case .lunch: return ("fork.knife", Pent.lunch, Pent.lunchBg)
        case .cancelled: return ("xmark.circle.fill", Pent.bad, Pent.badBg)
        case .payment: return ("doc.text.fill", Pent.proof, Pent.proofBg)
        case .activityJoined: return ("party.popper.fill", Pent.activ, Pent.activBg)
        case .activity: return ("calendar", Pent.activ, Pent.activBg)
        case .general: return ("bell.fill", Pent.neutral, Pent.neutralBg)
        }
    }
}
