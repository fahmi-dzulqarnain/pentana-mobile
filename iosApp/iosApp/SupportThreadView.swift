//
//  SupportThreadView.swift
//  iosApp
//
//  One question's conversation: the member's messages on the right in brand orange,
//  the committee's on the left (never an admin's name). Replying reopens a closed ticket.
//

@preconcurrency import Shared
import SwiftUI

struct SupportThreadView: View {
    @EnvironmentObject private var session: SessionStore
    let ticketId: Int64
    /// Lets the list refresh after a reply or resolve changes this ticket's status.
    var onChange: () -> Void = {}

    @State private var store: SupportThreadStore?
    @State private var state: SupportThreadUiState = SupportThreadUiStateLoading.shared
    @State private var inFlight: Set<Int64> = []
    @State private var actionError: String?
    /// Screenshots decoded once, as they arrive; the bubbles never decode (typing re-renders them).
    @State private var images: [Int64: Screenshot] = [:]
    @State private var draft = ""
    @State private var confirmingResolve = false
    @FocusState private var composerFocused: Bool

    private var sending: Bool { inFlight.contains(ticketId) }

    var body: some View {
        content
            .background(Pent.bgBase)
            .navigationBarTitleDisplayMode(.inline)
            .task {
                let activeStore = store ?? session.makeSupportThreadStore(ticketId: ticketId)
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                async let flights: Void = {
                    for await value in activeStore.inFlight {
                        let ids = Set(value.map { $0.int64Value })
                        await MainActor.run {
                            let finished = !inFlight.isEmpty && ids.isEmpty
                            inFlight = ids
                            if finished { onChange() }
                        }
                    }
                }()
                async let errors: Void = { for await value in activeStore.actionError { await MainActor.run { actionError = value } } }()
                async let pictures: Void = {
                    for await value in activeStore.images {
                        await MainActor.run {
                            var next = images
                            for entry in value {
                                // Decode each Loaded image once; already-decoded ones are kept as they are.
                                if case .image = next[entry.messageId] { continue }
                                next[entry.messageId] = Screenshot(entry.image)
                            }
                            images = next
                        }
                    }
                }()
                async let failedReplies: Void = {
                    for await value in activeStore.failedReply {
                        guard let text = value else { continue }
                        await MainActor.run {
                            // Put the unsent reply back, unless they've started typing something new.
                            if draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { draft = text }
                            activeStore.consumeFailedReply()
                        }
                    }
                }()
                _ = await (states, flights, errors, pictures, failedReplies)
            }
            .alert("Something went wrong", isPresented: Binding(get: { actionError != nil }, set: { if !$0 { store?.dismissActionError() } })) {
                Button("OK", role: .cancel) { store?.dismissActionError() }
            } message: {
                Text(actionError ?? "")
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "exclamationmark.bubble", tint: Pent.bad, bg: Pent.badBg,
                               title: "Couldn't open", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            thread(content.detail)
                .navigationTitle(content.detail.subject)
        }
    }

    private func thread(_ detail: SupportTicketDetailDto) -> some View {
        let status = supportStatus(status: detail.status)
        return ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 4) {
                    HStack(spacing: 6) {
                        SupportStatusPill(status: status, label: supportHeaderLabel(status: status))
                        StatusPill(supportTypeLabel(type: detail.type), color: Pent.label2, bg: Pent.surface2)
                    }
                    .padding(.bottom, 8)

                    ForEach(detail.messages, id: \.id) { message in
                        MessageBubble(message: message, image: images[message.id]) {
                            if let url = message.attachmentUrl { store?.loadImage(messageId: message.id, url: url) }
                        }
                        .id(message.id)
                    }

                    if let note = closedNote(detail: detail) {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Pent.ok)
                            Text(closedLine(note, detail.closedAt)).font(.pentFoot).foregroundStyle(Pent.label2)
                            Spacer()
                        }
                        .padding(12)
                        .background(Pent.neutralBg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .padding(.top, 10)
                    } else {
                        Button { confirmingResolve = true } label: {
                            Label("Mark as resolved", systemImage: "checkmark")
                                .font(.pentFoot).fontWeight(.semibold)
                        }
                        .tint(Pent.accent)
                        .disabled(inFlight.contains(-ticketId))
                        .padding(.top, 14)
                        .confirmationDialog("Mark this question as resolved?", isPresented: $confirmingResolve, titleVisibility: .visible) {
                            Button("Mark as resolved") { store?.resolve() }
                        } message: {
                            Text("You can still reply later to reopen it.")
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
            }
            .refreshable { try? await store?.refresh() }
            .onAppear { if let last = detail.messages.last { proxy.scrollTo(last.id, anchor: .bottom) } }
            .onChange(of: detail.messages.count) { _, _ in
                if let last = detail.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
            }
            .safeAreaInset(edge: .bottom) { composer(closed: status == .closed) }
        }
    }

    private func composer(closed: Bool) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField(closed ? "Reply to reopen…" : "Reply…", text: $draft, axis: .vertical)
                .lineLimit(1 ... 5)
                .focused($composerFocused)
                .font(.pentBody)
                .tint(Pent.accent)
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(Pent.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
            Button {
                let text = draft
                // Resign first: an input method still composing (e.g. Pinyin) writes its text back
                // into the binding after a plain clear, leaving the sent reply in the box.
                composerFocused = false
                draft = ""
                store?.reply(body: text)
            } label: {
                Group {
                    if sending { ProgressView().tint(.white) } else { Image(systemName: "arrow.up").font(.system(size: 15, weight: .bold)) }
                }
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(canSend ? Pent.accentSolid : Pent.label4, in: Circle())
            }
            .disabled(!canSend)
            .accessibilityLabel("Send reply")
        }
        .padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 10)
        .background(.bar)
    }

    private var canSend: Bool { !sending && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private func closedLine(_ note: String, _ closedAt: String?) -> String {
        guard let closedAt, let date = PentDates.dateTime(closedAt) else { return note }
        return "\(note) · \(date)"
    }
}

/// A screenshot as the bubble shows it. Bytes that don't decode count as failed, not loading.
private enum Screenshot {
    case loading
    case image(UIImage)
    case failed

    init(_ state: SupportImage) {
        if let loaded = state as? SupportImageLoaded {
            self = UIImage(data: dataFrom(bytes: loaded.bytes)).map { .image($0) } ?? .failed
        } else if state is SupportImageFailed {
            self = .failed
        } else {
            self = .loading
        }
    }
}

private struct MessageBubble: View {
    let message: SupportMessageDto
    let image: Screenshot?
    let loadImage: () -> Void

    var body: some View {
        VStack(alignment: message.isMine ? .trailing : .leading, spacing: 3) {
            Text(header).font(.pentCap).foregroundStyle(Pent.label3).padding(.horizontal, 6).padding(.top, 8)
            Text(message.body)
                .font(.pentBody)
                .foregroundStyle(message.isMine ? Pent.onBrand : Pent.label)
                .textSelection(.enabled)
                .padding(.horizontal, 13).padding(.vertical, 9)
                .background(bubble)
                .frame(maxWidth: 290, alignment: message.isMine ? .trailing : .leading)
            if message.attachmentUrl != nil { screenshot }
        }
        .frame(maxWidth: .infinity, alignment: message.isMine ? .trailing : .leading)
    }

    private var header: String {
        let who = message.isMine ? "You" : "Committee"
        guard let when = PentDates.relative(message.createdAt) else { return who }
        return "\(who) · \(when)"
    }

    @ViewBuilder private var bubble: some View {
        if message.isMine {
            UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 16, bottomTrailingRadius: 5, topTrailingRadius: 16, style: .continuous)
                .fill(LinearGradient(colors: [Pent.accentSolid, Pent.orange600], startPoint: .top, endPoint: .bottom))
        } else {
            UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 5, bottomTrailingRadius: 16, topTrailingRadius: 16, style: .continuous)
                .fill(Pent.surface)
                .overlay(UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 5, bottomTrailingRadius: 16, topTrailingRadius: 16, style: .continuous)
                    .strokeBorder(Pent.separator, lineWidth: 0.5))
        }
    }

    @ViewBuilder private var screenshot: some View {
        Group {
            switch image {
            case .image(let uiImage):
                Image(uiImage: uiImage).resizable().scaledToFit()
                    .accessibilityLabel("Screenshot")
            case .failed:
                Label("Screenshot unavailable", systemImage: "photo.badge.exclamationmark")
                    .font(.pentFoot).foregroundStyle(Pent.label3)
                    .frame(width: 180, height: 110)
                    .background(Pent.surface2)
            case .loading, nil:
                ProgressView().frame(width: 180, height: 110).background(Pent.surface2)
            }
        }
        .frame(maxWidth: 220, maxHeight: 260)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .onAppear(perform: loadImage)
    }
}
