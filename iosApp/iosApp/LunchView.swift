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
        List {
            if lunches.isEmpty && !isLoading {
                Text("No upcoming lunches.").foregroundStyle(.secondary)
            }
            ForEach(lunches, id: \.id) { lunch in
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(lunch.date).font(.headline)
                        if let menu = lunch.menu { Text(menu).font(.subheadline) }
                        if let caterer = lunch.caterer {
                            Text(caterer).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)

                    if lunch.isOpen {
                        ForEach(lunch.options, id: \.mealOptionId) { option in
                            optionRow(
                                title: option.name ?? "Option",
                                selected: lunch.responded && lunch.myMealOptionId?.int64Value == option.mealOptionId,
                                busy: busyLunchId == lunch.id
                            ) {
                                await update(lunch) {
                                    try await session.lunch.chooseOption(lunchId: lunch.id, mealOptionId: option.mealOptionId)
                                }
                            }
                        }
                        optionRow(
                            title: "Not attending",
                            selected: lunch.responded && lunch.myMealOptionId == nil,
                            busy: busyLunchId == lunch.id
                        ) {
                            await update(lunch) {
                                try await session.lunch.markNotAttending(lunchId: lunch.id)
                            }
                        }
                    } else {
                        Text(closedSummary(lunch)).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .overlay { if isLoading && lunches.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
    }

    @ViewBuilder
    private func optionRow(title: String, selected: Bool, busy: Bool, action: @escaping () async -> Void) -> some View {
        Button { Task { await action() } } label: {
            HStack {
                Text(title).foregroundStyle(.primary)
                Spacer()
                if selected { Image(systemName: "checkmark.circle.fill").foregroundStyle(.tint) }
            }
        }
        .disabled(busy)
    }

    private func closedSummary(_ lunch: LunchDto) -> String {
        guard lunch.responded else { return "Ordering closed — no order placed." }
        if let id = lunch.myMealOptionId?.int64Value,
           let name = lunch.options.first(where: { $0.mealOptionId == id })?.name {
            return "Ordering closed — you ordered \(name)."
        }
        return "Ordering closed — you marked not attending."
    }

    private func update(_ lunch: LunchDto, _ operation: () async throws -> LunchDto) async {
        busyLunchId = lunch.id
        do {
            let updated = try await operation()
            if let index = lunches.firstIndex(where: { $0.id == lunch.id }) {
                lunches[index] = updated
            }
        } catch {
            // Keep current state; a banner could be added later.
        }
        busyLunchId = nil
    }

    private func load() async {
        isLoading = true
        do {
            lunches = try await session.lunch.lunches()
        } catch {
            // Keep what we have.
        }
        isLoading = false
    }
}
