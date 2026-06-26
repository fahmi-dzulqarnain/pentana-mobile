//
//  LunchView.swift
//  iosApp
//

import Shared
import SwiftUI

struct LunchView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var lunches: [LunchDto] = []
    @State private var isLoading = true
    @State private var busyLunchId: Int64?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if lunches.isEmpty && !isLoading {
                    EmptyStateView(symbol: "fork.knife", tint: Pent.lunch, bg: Pent.lunchBg,
                                   title: "No upcoming lunches", message: "New catered lunches show up here to vote on.")
                }
                ForEach(lunches, id: \.id) { lunch in
                    LunchCard(lunch: lunch, busy: busyLunchId == lunch.id,
                              choose: { opt in await update(lunch) { try await session.lunch.chooseOption(lunchId: lunch.id, mealOptionId: opt) } },
                              notAttending: { await update(lunch) { try await session.lunch.markNotAttending(lunchId: lunch.id) } })
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
        .overlay { if isLoading && lunches.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
    }

    private func update(_ lunch: LunchDto, _ op: @escaping () async throws -> LunchDto) async {
        busyLunchId = lunch.id
        do {
            let updated = try await op()
            if let i = lunches.firstIndex(where: { $0.id == lunch.id }) { lunches[i] = updated }
        } catch {}
        busyLunchId = nil
    }

    private func load() async {
        isLoading = true
        do { lunches = try await session.lunch.lunches() } catch {}
        isLoading = false
    }
}

private struct LunchCard: View {
    let lunch: LunchDto
    let busy: Bool
    let choose: (Int64) async -> Void
    let notAttending: () async -> Void

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
                ForEach(Array(lunch.options.enumerated()), id: \.element.mealOptionId) { _, option in
                    PentHairline()
                    optionRow(title: option.name ?? "Option",
                              chosen: lunch.responded && lunch.myMealOptionId?.int64Value == option.mealOptionId) {
                        Task { await choose(option.mealOptionId) }
                    }
                }
                PentHairline()
                optionRow(title: "Not attending",
                          chosen: lunch.responded && lunch.myMealOptionId == nil) {
                    Task { await notAttending() }
                }
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "lock.fill").font(.system(size: 13)).foregroundStyle(Pent.label3)
                    Text(closedSummary).font(.pentFoot).fontWeight(.medium).foregroundStyle(Pent.label2)
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
        .opacity(busy ? 0.6 : 1)
        .allowsHitTesting(!busy)
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
        if !lunch.isOpen { return .closed }
        return lunch.responded ? .responded : .voteNow
    }
    private var deadlineText: String {
        guard let dl = lunch.deadline, let when = PentDates.dateTime(dl) else {
            return lunch.isOpen ? "Ordering open" : "Ordering closed"
        }
        return lunch.isOpen ? "Order by \(when)" : "Ordering closed \(when)"
    }
    private var closedSummary: String {
        guard lunch.responded else { return "Ordering closed — no order placed." }
        if let id = lunch.myMealOptionId?.int64Value,
           let name = lunch.options.first(where: { $0.mealOptionId == id })?.name {
            return "Ordering closed — you ordered \(name)."
        }
        return "Ordering closed — you marked not attending."
    }
}
