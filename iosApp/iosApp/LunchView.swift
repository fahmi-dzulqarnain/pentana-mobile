//
//  LunchView.swift
//  iosApp
//

@preconcurrency import Shared
import SwiftUI

struct LunchView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: LunchStore?
    @State private var state: LunchUiState = LunchUiStateLoading.shared
    @State private var inFlight: Set<Int64> = []
    @State private var actionError: String?

    var body: some View {
        content
            .task {
                let s = store ?? session.makeLunchStore()
                store = s
                // Collect the store's flows as structured children so they're cancelled when the
                // view disappears and restart on reappear. The store is kept alive across
                // reappear — its scope only runs one-shot load/choose coroutines, so it needs
                // no explicit clear() (it's released when the view is destroyed).
                async let states: Void = { for await value in s.state { await MainActor.run { state = value } } }()
                async let flights: Void = { for await ids in s.inFlight { await MainActor.run { inFlight = Set(ids.map { $0.int64Value }) } } }()
                async let errors: Void = { for await e in s.actionError { await MainActor.run { actionError = e } } }()
                _ = await (states, flights, errors)
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

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let e):
            ScrollView {
                EmptyStateView(symbol: "exclamationmark.triangle", tint: Pent.warn, bg: Pent.warnBg,
                               title: "Couldn't load lunches", message: e.message,
                               actionTitle: "Try again", action: { store?.load() })
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { store?.refresh() }
        case .content(let c):
            ScrollView {
                if c.lunches.isEmpty {
                    EmptyStateView(symbol: "fork.knife", tint: Pent.lunch, bg: Pent.lunchBg,
                                   title: "No upcoming lunches", message: "New catered lunches show up here to vote on.")
                        .containerRelativeFrame(.vertical, alignment: .center)
                } else {
                    VStack(spacing: 14) {
                        ForEach(c.lunches, id: \.id) { lunch in
                            LunchCard(
                                lunch: lunch,
                                busy: inFlight.contains(lunch.id),
                                choose: { opt in store?.choose(lunchId: lunch.id, mealOptionId: opt) },
                                notAttending: { store?.notAttending(lunchId: lunch.id) })
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
            .refreshable { store?.refresh() }
        }
    }
}

private struct LunchCard: View {
    let lunch: LunchDto
    let busy: Bool
    let choose: (Int64) -> Void
    let notAttending: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(lunch.menu ?? "Lunch").font(.pentHeadline).foregroundStyle(Pent.label)
                    Text(subtitle).font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Spacer(minLength: 8)
                StatusPill(statusKind)
            }
            .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 12)

            if lunch.isOpen {
                VStack(spacing: 0) {
                    ForEach(Array(lunch.options.enumerated()), id: \.element.mealOptionId) { _, option in
                        PentHairline()
                        optionRow(title: option.name ?? "Option",
                                  chosen: lunch.responded && lunch.myMealOptionId?.int64Value == option.mealOptionId) {
                            choose(option.mealOptionId)
                        }
                    }
                    PentHairline()
                    optionRow(title: "Not attending",
                              chosen: lunch.responded && lunch.myMealOptionId == nil) {
                        notAttending()
                    }
                }
                .opacity(busy ? 0.6 : 1)
                .allowsHitTesting(!busy)
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "lock.fill").font(.system(size: 13)).foregroundStyle(Pent.label3)
                    Text(lunchClosedSummary(lunch: lunch)).font(.pentFoot).fontWeight(.medium).foregroundStyle(Pent.label2)
                    Spacer()
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(Pent.surface2)
            }

            HStack(spacing: 6) {
                Image(systemName: "clock.fill").font(.system(size: 12)).foregroundStyle(Pent.label3)
                Text(deadlineText).font(.pentCap).foregroundStyle(Pent.label3)
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 9)
            .overlay(alignment: .top) { PentHairline(leadingInset: 0) }
        }
        .background(Pent.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
    }

    private func optionRow(title: String, chosen: Bool, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            HStack(spacing: 12) {
                Text(title)
                    .font(.pentBody).fontWeight(chosen ? .semibold : .medium)
                    .foregroundStyle(chosen ? Pent.lunch : Pent.label)
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
    }

    private var subtitle: String {
        let date = PentDates.shortDate(lunch.date)
        if let caterer = lunch.caterer, !caterer.isEmpty { return "\(date) · \(caterer)" }
        return date
    }
    private var statusKind: PillKind {
        switch lunchStatus(lunch: lunch) {
        case .voteNow: return .voteNow
        case .responded: return .responded
        case .closed: return .closed
        }
    }
    private var deadlineText: String {
        guard let dl = lunch.deadline, let when = PentDates.dateTime(dl) else {
            return lunch.isOpen ? "Ordering open" : "Ordering closed"
        }
        return lunch.isOpen ? "Order by \(when)" : "Ordering closed \(when)"
    }
}
