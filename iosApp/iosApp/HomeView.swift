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
                let s = store ?? session.makeHomeStore()
                store = s
                async let states: Void = { for await value in s.state { await MainActor.run { state = value } } }()
                _ = await states
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let e):
            ScrollView {
                EmptyStateView(symbol: "icloud.slash", tint: Pent.bad, bg: Pent.badBg,
                               title: "Couldn't load", message: e.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { store?.refresh() }
        case .content(let c):
            ScrollView {
                VStack(alignment: .leading, spacing: 11) {
                    Text(todayString)
                        .font(.pentSub).foregroundStyle(Pent.label2)
                        .padding(.horizontal, 4).padding(.bottom, 2)

                    duesCard(c.data)
                    lunchCard(c.data.nextLunch)
                    activityCard(c.data.nextActivity, openCount: Int(c.data.openActivitiesCount))
                    proofsCard(pending: Int(c.data.pendingProofsCount))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            .refreshable { store?.refresh() }
        }
    }

    // MARK: Cards

    @ViewBuilder
    private func duesCard(_ d: DashboardDto) -> some View {
        let cleared = duesCleared(d: d)
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
                    Text("MYR \(d.bills.totalOutstanding)").font(.pentMoney(19)).foregroundStyle(Pent.dues)
                    Text("outstanding").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Text("Credit MYR \(d.bills.availableCredit) · \(Int(d.bills.unpaidCount)) unpaid")
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
        let f = DateFormatter(); f.dateFormat = "EEEE, d MMMM"
        return f.string(from: Date())
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
        let p = DateFormatter(); p.dateFormat = "yyyy-MM-dd"; p.locale = Locale(identifier: "en_US_POSIX")
        guard let d = p.date(from: ymd) else { return ymd }
        let o = DateFormatter(); o.dateFormat = "EEE d MMM"
        return o.string(from: d)
    }
    /// ISO8601 -> "Thu 3 Jul · 7:00 AM"
    static func dateTime(_ iso: String) -> String? {
        let p = ISO8601DateFormatter(); p.formatOptions = [.withInternetDateTime]
        guard let d = p.date(from: iso) else { return nil }
        let o = DateFormatter(); o.dateFormat = "EEE d MMM · h:mm a"
        return o.string(from: d)
    }
    /// ISO8601 -> relative ("2h ago")
    static func relative(_ iso: String) -> String? {
        let p = ISO8601DateFormatter(); p.formatOptions = [.withInternetDateTime]
        guard let d = p.date(from: iso) else { return nil }
        let f = RelativeDateTimeFormatter(); f.unitsStyle = .abbreviated
        return f.localizedString(for: d, relativeTo: Date())
    }
}
