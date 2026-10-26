//
//  SupportListView.swift
//  iosApp
//
//  "Ask the committee" sheet: the member's questions (Open / Closed), each pushing its
//  conversation inside the sheet. Opened from the Home card or a support notification.
//

@preconcurrency import Shared
import SwiftUI

struct SupportListView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    /// A ticket to open straight away (from a notification tap), pushed on first appearance.
    var openTicketId: Int64? = nil

    @State private var store: SupportListStore?
    @State private var state: SupportListUiState = SupportListUiStateLoading.shared
    @State private var showClosed = false
    @State private var path: [Int64] = []
    @State private var showingNew = false
    @State private var openedInitial = false

    var body: some View {
        NavigationStack(path: $path) {
            content
                .background(Pent.bgBase)
                .navigationTitle("Ask the committee")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done") { dismiss() }.tint(Pent.accent)
                    }
                    ToolbarItem(placement: .primaryAction) {
                        Button { showingNew = true } label: { Image(systemName: "plus") }
                            .tint(Pent.accent)
                            .accessibilityLabel("New question")
                    }
                }
                .navigationDestination(for: Int64.self) { id in
                    SupportThreadView(ticketId: id) { Task { try? await store?.refresh() } }
                }
        }
        .sheet(isPresented: $showingNew) {
            if let store {
                NewSupportTicketView(store: store) { newId in path.append(newId) }
            }
        }
        .task {
            let activeStore = store ?? session.makeSupportListStore()
            store = activeStore
            if let openTicketId, !openedInitial {
                openedInitial = true
                path = [openTicketId]
            }
            for await value in activeStore.state { await MainActor.run { state = value } }
        }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "icloud.slash", tint: Pent.bad, bg: Pent.badBg,
                               title: "Couldn't load", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            let tickets = showClosed ? content.closed : content.open
            ScrollView {
                VStack(spacing: 0) {
                    Picker("Show", selection: $showClosed) {
                        Text("Open").tag(false)
                        Text("Closed").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .padding(.bottom, 4)

                    if tickets.isEmpty {
                        EmptyStateView(symbol: "lifepreserver", tint: Pent.accent, bg: Pent.lunchBg,
                                       title: showClosed ? "No closed questions" : "No questions yet",
                                       message: showClosed ? nil : "Ask the committee about payments, lunch, activities or the app.")
                    } else {
                        SectionLabel(text: showClosed ? "Closed" : "Your questions")
                        InsetGroup {
                            ForEach(Array(tickets.enumerated()), id: \.element.id) { index, ticket in
                                Button { path.append(ticket.id) } label: { SupportTicketRow(ticket: ticket) }
                                    .buttonStyle(.plain)
                                if index < tickets.count - 1 { PentHairline(leadingInset: 64) }
                            }
                        }
                    }

                    if !showClosed {
                        Button { showingNew = true } label: { Label("New question", systemImage: "square.and.pencil") }
                            .buttonStyle(PentProminentButtonStyle())
                            .padding(.top, 22)
                        Text("The committee usually replies within a few days.")
                            .font(.pentFoot).foregroundStyle(Pent.label3)
                            .multilineTextAlignment(.center)
                            .padding(.top, 10)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
            .refreshable { try? await store?.refresh() }
        }
    }
}

private struct SupportTicketRow: View {
    let ticket: SupportTicketDto

    var body: some View {
        let status = supportStatus(status: ticket.status)
        HStack(spacing: 12) {
            SupportTypeIcon(type: ticket.type)
            VStack(alignment: .leading, spacing: 2) {
                Text(ticket.subject).font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label).lineLimit(1)
                Text(subtitle).font(.pentFoot).foregroundStyle(Pent.label2)
            }
            Spacer(minLength: 6)
            SupportStatusPill(status: status, label: supportListLabel(status: status))
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Pent.label4)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        let who = ticket.lastMessageFromCommittee ? "Committee" : "You"
        guard let when = PentDates.relative(ticket.lastMessageAt) else { return who }
        return "\(who) · \(when)"
    }
}

/// Tinted tile for the ticket type, shared by the list and the conversation header.
struct SupportTypeIcon: View {
    let type: String
    var body: some View {
        switch type {
        case "bug": DomainIcon(symbol: "ladybug.fill", tint: Pent.bad, bg: Pent.badBg, size: 36, corner: 10, iconSize: 17)
        case "suggestion": DomainIcon(symbol: "lightbulb.fill", tint: Pent.activ, bg: Pent.activBg, size: 36, corner: 10, iconSize: 17)
        default: DomainIcon(symbol: "questionmark.bubble.fill", tint: Pent.dues, bg: Pent.duesBg, size: 36, corner: 10, iconSize: 17)
        }
    }
}

/// The shared status mapped to this platform's pill colours.
struct SupportStatusPill: View {
    let status: SupportStatus
    let label: String
    var body: some View {
        switch status {
        case .replied: StatusPill(label, color: Pent.warn, bg: Pent.warnBg)
        case .closed: StatusPill(label, color: Pent.neutral, bg: Pent.neutralBg)
        default: StatusPill(label, color: Pent.dues, bg: Pent.duesBg)
        }
    }
}
