//
//  LunchView.swift
//  iosApp
//

@preconcurrency import Shared
import SwiftUI
import UIKit

struct LunchView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: LunchStore?
    @State private var state: LunchUiState = LunchUiStateLoading.shared
    @State private var inFlight: Set<Int64> = []
    @State private var actionError: String?
    @State private var lastChange: LunchChange?
    @State private var sheet: SheetTarget?

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeLunchStore()
                store = activeStore
                // Collect the store's flows as structured children so they're cancelled when the
                // view disappears and restart on reappear. The store is kept alive across
                // reappear - its scope only runs one-shot load/choose coroutines, so it needs
                // no explicit clear() (it's released when the view is destroyed).
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                async let flights: Void = { for await ids in activeStore.inFlight { await MainActor.run { inFlight = Set(ids.map { $0.int64Value }) } } }()
                async let errors: Void = { for await message in activeStore.actionError { await MainActor.run { actionError = message } } }()
                async let changes: Void = { for await change in activeStore.lastChange { await MainActor.run { lastChange = change } } }()
                _ = await (states, flights, errors, changes)
            }
            // One success tap per saved pick (seq grows every save), one error buzz per failed save.
            .onChange(of: lastChange?.seq) { _, seq in
                if seq != nil { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            }
            .onChange(of: actionError) { _, message in
                if message != nil { UINotificationFeedbackGenerator().notificationOccurred(.error) }
            }
            .sheet(item: $sheet) { target in
                LunchSheetView(
                    lunches: lunches,
                    lunchId: target.id,
                    inFlight: inFlight,
                    change: lastChange,
                    choose: { lunchId, optionId in store?.choose(lunchId: lunchId, lunchOptionId: optionId) },
                    notAttending: { lunchId in store?.notAttending(lunchId: lunchId) },
                    undo: { store?.undo() },
                    dismissChange: { store?.dismissChange() },
                    open: { sheet = SheetTarget(id: $0) },
                    close: { sheet = nil }
                )
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            .alert(
                "Something went wrong",
                isPresented: Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil; store?.dismissActionError() } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(actionError ?? "")
            }
    }

    private var lunches: [LunchDto] {
        if case .content(let content) = onEnum(of: state) { return content.lunches }
        return []
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "exclamationmark.triangle", tint: Pent.warn, bg: Pent.warnBg,
                               title: "Couldn't load lunches", message: error.message,
                               actionTitle: "Try again", action: { store?.load() })
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            ScrollView {
                if content.lunches.isEmpty {
                    EmptyStateView(symbol: "fork.knife", tint: Pent.lunch, bg: Pent.lunchBg,
                                   title: "No upcoming lunches", message: "New catered lunches show up here to vote on.")
                        .containerRelativeFrame(.vertical, alignment: .center)
                } else {
                    // Re-derive every minute so countdowns tick and a passed deadline closes its day.
                    TimelineView(.everyMinute) { context in
                        LunchAgendaList(agenda: LunchClock.agenda(content.lunches, at: context.date),
                                        open: { sheet = SheetTarget(id: $0) })
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
            .refreshable { try? await store?.refresh() }
            .overlay(alignment: .bottom) {
                // The sheet shows its own toast while it's open.
                if sheet == nil, let change = lastChange {
                    LunchToast(change: change, undo: { store?.undo() }, dismiss: { store?.dismissChange() })
                        .padding(.bottom, 12)
                }
            }
        }
    }
}

private struct SheetTarget: Identifiable, Equatable { let id: Int64 }

/// The shared agenda functions take epoch seconds and the device's UTC offset.
enum LunchClock {
    static func agenda(_ lunches: [LunchDto], at date: Date) -> LunchAgenda {
        lunchAgenda(lunches: lunches, nowEpochSeconds: Int64(date.timeIntervalSince1970),
                    utcOffsetSeconds: Int32(TimeZone.current.secondsFromGMT(for: date)))
    }

    static func sheet(_ lunches: [LunchDto], lunchId: Int64, at date: Date) -> Shared.LunchSheet? {
        lunchSheet(lunches: lunches, lunchId: lunchId, nowEpochSeconds: Int64(date.timeIntervalSince1970),
                   utcOffsetSeconds: Int32(TimeZone.current.secondsFromGMT(for: date)))
    }
}

// MARK: - Agenda

struct LunchAgendaList: View {
    let agenda: LunchAgenda
    let open: (Int64) -> Void

    var body: some View {
        VStack(spacing: 0) {
            if let banner = agenda.banner {
                LunchBannerView(banner: banner, open: open)
                    .padding(.top, 4)
            }
            ForEach(agenda.sections, id: \.title) { section in
                SectionLabel(text: section.title)
                InsetGroup {
                    ForEach(Array(section.rows.enumerated()), id: \.element.lunchId) { index, row in
                        if index > 0 { PentHairline(leadingInset: row.isToday || section.rows[index - 1].isToday ? 0 : 72) }
                        LunchAgendaRowView(row: row) { open(row.lunchId) }
                    }
                }
            }
            Text("No more lunches published yet")
                .font(.pentSub).foregroundStyle(Pent.label2)
                .frame(maxWidth: .infinity)
                .padding(.top, 16)
        }
    }
}

private struct LunchBannerView: View {
    let banner: LunchBanner
    let open: (Int64) -> Void

    var body: some View {
        // Button beside the text when every line fits on one line, else under it.
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .center, spacing: 12) {
                text.fixedSize()
                Spacer(minLength: 0)
                button
            }
            VStack(alignment: .leading, spacing: 10) {
                text
                button
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Pent.warnBg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var text: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(banner.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(Pent.lunchInk)
            ForEach(banner.lines, id: \.self) { line in
                Text(line).font(.pentFoot).foregroundStyle(Pent.lunchInk)
            }
        }
    }

    private var button: some View {
        Button(banner.actionLabel) { open(banner.targetLunchId) }
            .buttonStyle(LunchPillButtonStyle())
    }
}

private struct LunchAgendaRowView: View {
    let row: LunchAgendaRow
    let tap: () -> Void

    var body: some View {
        Button(action: tap) {
            Group {
                if row.kind == .past && !row.isToday { compact } else { full }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibilityLabel)
        .accessibilityHint(hint)
        .accessibilityAddTraits(.isButton)
    }

    /// A day before today: one dimmed line.
    private var compact: some View {
        HStack(spacing: 12) {
            Text("\(row.weekday) \(row.day)")
                .font(.system(size: 12, weight: .semibold)).foregroundStyle(Pent.label2)
                .lineLimit(1).fixedSize()
                .frame(width: 48)
            (Text(row.title).foregroundStyle(Pent.label2)
                + Text(row.detail.map { " · \($0)" } ?? "").foregroundStyle(Pent.label3))
                .font(.pentCallout)
                .lineLimit(1)
            Spacer(minLength: 4)
            chevron
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
    }

    private var full: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(spacing: 0) {
                Text(row.weekday).font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(row.isToday || row.kind == .needsVote ? Pent.lunchInk : Pent.label2)
                Text(row.day).font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(row.isToday ? Pent.lunchInk : Pent.label)
            }
            .frame(width: 48)

            VStack(alignment: .leading, spacing: 2) {
                if let eyebrow = row.eyebrow {
                    Text(eyebrow.uppercased()).font(.pentCap).tracking(0.3).foregroundStyle(Pent.lunchInk)
                }
                if row.isToday {
                    Text(row.title).font(.pentTitle3).foregroundStyle(Pent.label)
                    if let detail = row.detail { Text(detail).font(.pentFoot).foregroundStyle(Pent.label2) }
                } else {
                    (Text(row.title).font(.system(size: 16, weight: row.kind == .needsVote ? .semibold : .medium))
                        .foregroundStyle(row.kind == .closed ? Pent.label2 : Pent.label)
                        + Text(row.detail.map { " · \($0)" } ?? "").font(.pentFoot).foregroundStyle(Pent.label2))
                }
                if let subtitle = row.subtitle {
                    Text(subtitle).font(.system(size: 12.5, weight: row.urgent ? .semibold : .regular))
                        .foregroundStyle(row.urgent ? Pent.lunchInk : Pent.label2)
                }
                if let preview = row.menuPreview {
                    Text(preview).font(.pentFoot).foregroundStyle(Pent.label3).lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            switch row.action {
            case .vote:
                Text("Vote").font(.system(size: 14, weight: .semibold)).foregroundStyle(Pent.onBrand)
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .background(Pent.accentSolid, in: Capsule())
            case .change:
                Text("Change").font(.system(size: 14, weight: .semibold)).foregroundStyle(Pent.lunchInk)
            case .view:
                if !row.isToday { chevron }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, row.isToday ? 16 : 12)
        .background(row.isToday ? Pent.lunchBg : Color.clear)
    }

    private var chevron: some View {
        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Pent.label4)
    }

    private var hint: String {
        switch row.action {
        case .vote: return "Opens the menu to vote"
        case .change: return "Opens the menu to change your pick"
        case .view: return "Shows this day's lunch"
        }
    }
}

private struct LunchPillButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(Pent.onBrand)
            .padding(.horizontal, 16).frame(minHeight: 44)
            .background(Pent.accentSolid.opacity(configuration.isPressed ? 0.8 : 1), in: Capsule())
    }
}

// MARK: - Toast

private struct LunchToast: View {
    let change: LunchChange
    let undo: () -> Void
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Label(change.message, systemImage: "checkmark.circle.fill")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
            if change.canUndo {
                Button("Undo", action: undo)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(hex: 0xFBB04C))
                    .frame(minHeight: 44)
            }
        }
        .padding(.leading, 16).padding(.trailing, change.canUndo ? 10 : 16).padding(.vertical, change.canUndo ? 0 : 12)
        .background(Color(white: 0.12), in: Capsule())
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        .transition(.move(edge: .bottom).combined(with: .opacity))
        // Long enough to reach Undo; restarts for every new save.
        .task(id: change.seq) {
            try? await Task.sleep(for: .seconds(6))
            dismiss()
        }
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Sheet

struct LunchSheetView: View {
    let lunches: [LunchDto]
    let lunchId: Int64
    let inFlight: Set<Int64>
    let change: LunchChange?
    let choose: (Int64, Int64) -> Void
    let notAttending: (Int64) -> Void
    let undo: () -> Void
    let dismissChange: () -> Void
    let open: (Int64) -> Void
    let close: () -> Void

    var body: some View {
        TimelineView(.everyMinute) { context in
            if let sheet = LunchClock.sheet(lunches, lunchId: lunchId, at: context.date) {
                content(sheet)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Pent.bgGrouped)
        .overlay(alignment: .bottom) {
            if let change, change.lunchId == lunchId {
                LunchToast(change: change, undo: undo, dismiss: dismissChange).padding(.bottom, 12)
            }
        }
    }

    private func content(_ sheet: Shared.LunchSheet) -> some View {
        let busy = inFlight.contains(sheet.lunchId)
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(sheet.title).font(.pentTitle2).foregroundStyle(Pent.label)
                        Text(sheet.status).font(.system(size: 14, weight: sheet.urgent ? .semibold : .regular))
                            .foregroundStyle(sheet.urgent ? Pent.lunchInk : Pent.label2)
                    }
                    Spacer()
                    Button("Close", action: close)
                        .font(.system(size: 16, weight: .semibold)).foregroundStyle(Pent.lunchInk)
                        .frame(minHeight: 44)
                }
                .padding(.top, 20)

                if sheet.isOpen {
                    InsetGroup {
                        ForEach(Array(sheet.menus.enumerated()), id: \.offset) { index, menu in
                            if index > 0 { PentHairline(leadingInset: 0) }
                            menuHeading(menu.heading)
                            ForEach(menu.options, id: \.lunchOptionId) { option in
                                PentHairline()
                                optionRow(option.name, chosen: option.selected) { choose(sheet.lunchId, option.lunchOptionId) }
                            }
                        }
                        // Older server (no menus): legacy options carry no lunchOptionId, so they are shown read-only rather than guessing an id.
                        ForEach(Array(sheet.legacyOptions.enumerated()), id: \.offset) { index, option in
                            if index > 0 { PentHairline() }
                            optionRow(option.name, chosen: option.selected) {}.disabled(true)
                        }
                    }
                    InsetGroup {
                        optionRow("Not attending", chosen: sheet.notAttendingSelected, muted: true) { notAttending(sheet.lunchId) }
                    }
                    Text("Your pick saves as soon as you tap it. You can change it until the deadline.")
                        .font(.pentFoot).foregroundStyle(Pent.label2)
                        .padding(.horizontal, 4)
                    if let next = sheet.next {
                        Button { open(next.lunchId) } label: {
                            HStack {
                                Text(next.label).font(.system(size: 16, weight: .semibold))
                                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundStyle(Pent.lunchInk)
                            .frame(maxWidth: .infinity, minHeight: 44)
                        }
                    }
                } else {
                    // A closed day shows just what happened and what was on offer.
                    InsetGroup {
                        HStack(spacing: 8) {
                            Image(systemName: "lock.fill").font(.system(size: 13)).foregroundStyle(Pent.label3)
                            Text(sheet.closedSummary ?? "Ordering closed.").font(.pentSub).fontWeight(.medium).foregroundStyle(Pent.label)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 14)
                        ForEach(Array(sheet.menus.enumerated()), id: \.offset) { _, menu in
                            PentHairline(leadingInset: 0)
                            menuHeading(menu.heading)
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 90)
            .opacity(busy ? 0.6 : 1)
            .allowsHitTesting(!busy)
        }
    }

    private func menuHeading(_ text: String) -> some View {
        Text(text).font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.label2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 6)
    }

    private func optionRow(_ title: String, chosen: Bool, muted: Bool = false, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            HStack(spacing: 12) {
                Text(title)
                    .font(.pentBody).fontWeight(chosen ? .semibold : .medium)
                    .foregroundStyle(chosen ? Pent.lunchInk : (muted ? Pent.label2 : Pent.label))
                Spacer()
                ZStack {
                    Circle()
                        .fill(chosen ? Pent.lunch : Color.clear)
                        .frame(width: 24, height: 24)
                        .overlay(Circle().strokeBorder(chosen ? Color.clear : Pent.label4, lineWidth: 1.5))
                    if chosen { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white) }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
            .background(chosen ? Pent.lunchBg : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }
}
