//
//  HomeView.swift
//  iosApp
//
//  Dashboard: glass summary cards (Dues · Lunch · Activities · Proofs) over the
//  ambient field, from the single GET /dashboard aggregate. Cards switch tabs.
//

@preconcurrency import Shared
import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var selection: Int
    @State private var store: HomeStore?
    @State private var state: HomeUiState = HomeUiStateLoading.shared

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeHomeStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                _ = await states
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "icloud.slash", tint: Pent.bad, bg: Pent.badBg,
                               title: "Couldn't load", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { store?.refresh() }
        case .content(let content):
            ScrollView {
                VStack(alignment: .leading, spacing: 11) {
                    Text(todayString)
                        .font(.pentSub).foregroundStyle(Pent.label2)
                        .padding(.horizontal, 4).padding(.bottom, 2)

                    duesCard(content.data)
                    lunchCard(content.data.nextLunch)
                    activityCard(content.data.nextActivity, openCount: Int(content.data.openActivitiesCount))
                    proofsCard(pending: Int(content.data.pendingProofsCount))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            .refreshable { store?.refresh() }
        }
    }

    // MARK: Cards

    @ViewBuilder
    private func duesCard(_ dashboard: DashboardDto) -> some View {
        let cleared = duesCleared(dashboard: dashboard)
        if cleared {
            HStack(spacing: 13) {
                Circle().fill(Pent.okBg).frame(width: 46, height: 46)
                    .overlay(Image(systemName: "party.popper.fill").font(.system(size: 22)).foregroundStyle(Pent.ok))
                VStack(alignment: .leading, spacing: 2) {
                    Text("You're all clear").font(.pentHeadline).foregroundStyle(Pent.label)
                    Text("No dues, nothing pending. Nice.").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .pentGlass(20)
        } else {
            HomeCard(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg, title: "Dues") { selection = 1 } content: {
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text("MYR \(dashboard.bills.totalOutstanding)").font(.pentMoney(19)).foregroundStyle(Pent.dues)
                    Text("outstanding").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Text("Credit MYR \(dashboard.bills.availableCredit) · \(Int(dashboard.bills.unpaidCount)) unpaid")
                    .font(.pentFoot).foregroundStyle(Pent.label2)
            }
        }
    }

    @ViewBuilder
    private func lunchCard(_ lunch: DashboardLunchDto?) -> some View {
        HomeCard(symbol: "fork.knife", tint: Pent.lunch, bg: Pent.lunchBg, title: "Next lunch") { selection = 2 } content: {
            if let lunch {
                Text(lunchLine(lunch)).font(.pentCallout).fontWeight(.medium).foregroundStyle(Pent.label)
                switch dashboardLunchStatus(lunch: lunch) {
                case .voteNow: StatusPill(.voteNow).padding(.top, 4)
                case .responded: StatusPill(.responded).padding(.top, 4)
                case .closed: StatusPill(.closed).padding(.top, 4)
                }
            } else {
                Text("None scheduled").font(.pentCallout).foregroundStyle(Pent.label2)
            }
        }
    }

    @ViewBuilder
    private func activityCard(_ activity: DashboardActivityDto?, openCount: Int) -> some View {
        HomeCard(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg, title: "Activities") { selection = 3 } content: {
            if let activity {
                Text(activity.title).font(.pentCallout).fontWeight(.medium).foregroundStyle(Pent.label)
                HStack(spacing: 6) {
                    // nextActivity is always one of the member's registrations, so anything non-waitlisted
                    // renders as Registered (matches this platform's previous behaviour; .none is theoretical).
                    StatusPill(dashboardActivityStatus(activity: activity) == .waitlisted ? .waitlisted : .registered)
                    if openCount > 0 {
                        Text("· \(openCount) open to join").font(.pentFoot).foregroundStyle(Pent.label2)
                    }
                }
                .padding(.top, 3)
            } else {
                Text("No upcoming registrations").font(.pentCallout).foregroundStyle(Pent.label2)
                if openCount > 0 {
                    Text("\(openCount) open to join").font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.activ).padding(.top, 3)
                }
            }
        }
    }

    @ViewBuilder
    private func proofsCard(pending: Int) -> some View {
        HomeCard(symbol: "doc.text.fill", tint: Pent.proof, bg: Pent.proofBg, title: "Payment proofs") { selection = 1 } content: {
            if pending > 0 {
                Text("\(pending) awaiting review").font(.pentCallout).fontWeight(.medium).foregroundStyle(Pent.label)
            } else {
                Text("Nothing pending").font(.pentCallout).foregroundStyle(Pent.label2)
            }
        }
    }

    // MARK: Data + format

    private var todayString: String {
        let formatter = DateFormatter(); formatter.dateFormat = "EEEE, d MMMM"
        return formatter.string(from: Date())
    }

    private func lunchLine(_ lunch: DashboardLunchDto) -> String {
        let date = PentDates.shortDate(lunch.date)
        if let menu = lunch.menu, !menu.isEmpty { return "\(date) · \(menu)" }
        return date
    }
}

// MARK: - Home card

struct HomeCard<Content: View>: View {
    let symbol: String
    let tint: Color
    let bg: Color
    let title: String
    let onTap: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 13) {
                DomainIcon(symbol: symbol, tint: tint, bg: bg, size: 44, corner: 13, iconSize: 22)
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text(title).font(.pentHeadline).foregroundStyle(Pent.label)
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(Pent.label4)
                    }
                    VStack(alignment: .leading, spacing: 0) { content }
                }
            }
            .padding(15)
        }
        .buttonStyle(.plain)
        .pentGlass(20)
    }
}

// MARK: - Shared date formatting

enum PentDates {
    /// "2026-06-30" -> "Mon 30 Jun"
    static func shortDate(_ ymd: String) -> String {
        let parser = DateFormatter(); parser.dateFormat = "yyyy-MM-dd"; parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: ymd) else { return ymd }
        let formatter = DateFormatter(); formatter.dateFormat = "EEE d MMM"
        return formatter.string(from: date)
    }
    /// ISO8601 -> "Thu 3 Jul · 7:00 AM"
    static func dateTime(_ iso: String) -> String? {
        let parser = ISO8601DateFormatter(); parser.formatOptions = [.withInternetDateTime]
        guard let date = parser.date(from: iso) else { return nil }
        let formatter = DateFormatter(); formatter.dateFormat = "EEE d MMM · h:mm a"
        return formatter.string(from: date)
    }
    /// ISO8601 -> relative ("2h ago")
    static func relative(_ iso: String) -> String? {
        let parser = ISO8601DateFormatter(); parser.formatOptions = [.withInternetDateTime]
        guard let date = parser.date(from: iso) else { return nil }
        let formatter = RelativeDateTimeFormatter(); formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
