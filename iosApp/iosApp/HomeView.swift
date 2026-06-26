//
//  HomeView.swift
//  iosApp
//
//  Dashboard: a one-glance summary across Bills, Lunch, Activities and payment
//  proofs, from the single GET /dashboard aggregate. Each card taps through to
//  its full tab.
//

import Shared
import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var selection: Int
    @State private var dashboard: DashboardDto?
    @State private var isLoading = true

    var body: some View {
        List {
            if let name = session.user?.name {
                Section { Text("Hi, \(name)").font(.title2.bold()) }
            }

            if let d = dashboard {
                duesCard(d.bills)
                lunchCard(d.nextLunch)
                activityCard(d.nextActivity, openCount: Int(d.openActivitiesCount))
                proofsCard(pending: Int(d.pendingProofsCount))
            } else if !isLoading {
                Section { Text("Couldn't load your summary. Pull to refresh.").foregroundStyle(.secondary) }
            }
        }
        .overlay { if isLoading && dashboard == nil { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
    }

    // MARK: - Cards

    @ViewBuilder
    private func duesCard(_ bills: DashboardBillsDto) -> some View {
        let cleared = bills.totalOutstanding == "0.00"
        card("Dues", system: "creditcard", tint: .blue, tab: 1) {
            Text(cleared ? "No dues outstanding" : "MYR \(bills.totalOutstanding) outstanding")
                .font(.subheadline)
                .foregroundStyle(cleared ? .secondary : .primary)
            Text("Credit MYR \(bills.availableCredit) · \(Int(bills.unpaidCount)) unpaid")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private func lunchCard(_ lunch: DashboardLunchDto?) -> some View {
        card("Next lunch", system: "fork.knife", tint: .orange, tab: 2) {
            if let lunch {
                Text(formattedDate(lunch.date)).font(.subheadline)
                if let menu = lunch.menu, !menu.isEmpty {
                    Text(menu).font(.caption).foregroundStyle(.secondary)
                }
                if lunch.isOpen && !lunch.responded {
                    Text("Vote now").font(.caption.bold()).foregroundStyle(.orange)
                } else if lunch.responded {
                    Text("You've responded").font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("Voting closed").font(.caption).foregroundStyle(.secondary)
                }
            } else {
                Text("None scheduled").font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func activityCard(_ activity: DashboardActivityDto?, openCount: Int) -> some View {
        card("Activities", system: "calendar", tint: .green, tab: 3) {
            if let activity {
                Text(activity.title).font(.subheadline)
                Text(activityDetail(activity)).font(.caption).foregroundStyle(.secondary)
            } else {
                Text("No upcoming registrations").font(.subheadline).foregroundStyle(.secondary)
            }
            if openCount > 0 {
                Text("\(openCount) open to join").font(.caption.bold()).foregroundStyle(.green)
            }
        }
    }

    @ViewBuilder
    private func proofsCard(pending: Int) -> some View {
        // Payment proofs are submitted/seen under the Bills tab.
        card("Payment proofs", system: "doc.text", tint: .purple, tab: 1) {
            if pending > 0 {
                Text("\(pending) awaiting review").font(.subheadline)
            } else {
                Text("Nothing pending").font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }

    private func card(
        _ title: String, system: String, tint: Color, tab: Int,
        @ViewBuilder content: () -> some View
    ) -> some View {
        Section {
            Button { selection = tab } label: {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: system).font(.title2).foregroundStyle(tint).frame(width: 30)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title).font(.headline).foregroundStyle(.primary)
                        content()
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                }
            }
        }
    }

    // MARK: - Load + format

    private func load() async {
        isLoading = true
        do {
            dashboard = try await session.dashboard.dashboard()
        } catch {
            // Keep what we have; the empty-state row prompts a refresh.
        }
        isLoading = false
    }

    private func activityDetail(_ activity: DashboardActivityDto) -> String {
        let status = activity.myStatus == "waitlisted" ? "Waitlisted" : "Registered"
        if let when = activity.startsAt.flatMap(formattedDateTime) {
            return "\(status) · \(when)"
        }
        return status
    }

    private func formattedDate(_ ymd: String) -> String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: ymd) else { return ymd }
        let out = DateFormatter()
        out.dateFormat = "EEE, d MMM"
        return out.string(from: date)
    }

    private func formattedDateTime(_ iso: String) -> String? {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime]
        guard let date = parser.date(from: iso) else { return nil }
        let out = DateFormatter()
        out.dateFormat = "d MMM • h:mm a"
        return out.string(from: date)
    }
}
