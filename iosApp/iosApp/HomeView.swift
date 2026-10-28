//
//  HomeView.swift
//  iosApp
//
//  Home: date + greeting, "Needs you" (the member's to-dos, most urgent first, capped at 3)
//  or the all-clear card, then "Coming up". Everything is decided by the shared
//  homeSections(dashboard:) so iOS and Android never disagree on which to-dos show.
//

@preconcurrency import Shared
import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var selection: Int
    /// A change asks Home to refetch (e.g. after the support sheet closes).
    var refreshToken: Int = 0
    /// Opens Ask the committee (owned by MainView): a ticket id opens that thread, nil the ticket list.
    var onOpenSupport: (Int64?) -> Void = { _ in }
    @State private var store: HomeStore?
    @State private var state: HomeUiState = HomeUiStateLoading.shared
    /// "+N more" expands the to-do list in place.
    @State private var showAllTodos = false
    /// Resubmitting a rejected proof: the submit-proof sheet needs a Bills store of its own.
    @State private var billsStore: BillsStore?
    /// The open resubmit sheet. Presented by item so its content is built with the store it
    /// carries; `.sheet(isPresented:)` captured a nil store on the first tap (blank sheet).
    @State private var resubmit: ResubmitSheet?

    // Dynamic Type for the texts set outside TodoRow / ComingUpRow / SectionHeader.
    @ScaledMetric(relativeTo: .footnote) private var dateSize: CGFloat = 13
    @ScaledMetric(relativeTo: .title2) private var greetingSize: CGFloat = 22
    @ScaledMetric(relativeTo: .footnote) private var secondarySize: CGFloat = 13
    @ScaledMetric(relativeTo: .footnote) private var openToJoinSize: CGFloat = 13.5
    @ScaledMetric(relativeTo: .subheadline) private var emptyTitleSize: CGFloat = 15

    private static let todoCap = 3

    private struct ResubmitSheet: Identifiable {
        let id = UUID()
        let store: BillsStore
    }

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeHomeStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                _ = await states
            }
            .onChange(of: refreshToken) { _, _ in Task { try? await store?.refresh() } }
            .sheet(item: $resubmit, onDismiss: {
                billsStore?.resetSubmit()
                Task { try? await store?.refresh() }
            }) { sheet in
                SubmitProofView(store: sheet.store)
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    greeting
                    HomeSkeleton()
                }
                .padding(.horizontal, 16)
            }
            .scrollDisabled(true)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "icloud.slash", tint: Pent.bad, bg: Pent.badBg,
                               title: "Couldn't load", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            let sections = homeSections(dashboard: content.data)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    greeting
                    if sections.todos.isEmpty {
                        allClearCard.padding(.top, 14)
                    } else {
                        needsYou(sections.todos)
                    }
                    comingUp(sections)
                    // Starting a new question; admins answer on the web, so members only.
                    if session.user?.isAdmin != true {
                        askCommitteeRow.padding(.top, 14)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
            .refreshable { try? await store?.refresh() }
        }
    }

    // MARK: Greeting

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(todayString)
                .font(.system(size: dateSize)).foregroundStyle(Pent.label2)
            Text("Hi, \(session.greetingName)")
                .font(.system(size: greetingSize, weight: .bold)).foregroundStyle(Pent.label)
                .accessibilityAddTraits(.isHeader)
        }
        .padding(.horizontal, 4)
        .padding(.top, 4)
    }

    // MARK: Needs you

    @ViewBuilder
    private func needsYou(_ todos: [HomeTodo]) -> some View {
        let visible = showAllTodos ? todos : Array(todos.prefix(Self.todoCap))
        SectionHeader(title: "Needs you", count: todos.count)
        VStack(spacing: 0) {
            ForEach(Array(visible.enumerated()), id: \.offset) { index, todo in
                if index > 0 { HomeRowDivider() }
                TodoRow(model: TodoRowModel(todo: todo)) { open(todo) }
            }
            if !showAllTodos && todos.count > Self.todoCap {
                HomeRowDivider()
                moreRow(hidden: todos.count - Self.todoCap, next: TodoRowModel(todo: todos[Self.todoCap]).title)
            }
        }
        .pentGlass(20)
    }

    private func moreRow(hidden: Int, next: String) -> some View {
        Button {
            withAnimation(.easeOut(duration: 0.2)) { showAllTodos = true }
        } label: {
            HStack(spacing: 8) {
                Text("+\(hidden) more · \(next)")
                    .font(.system(size: secondarySize, weight: .semibold))
                    .foregroundStyle(Pent.label2)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HomeChevron()
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Shows the rest of the list")
    }

    private func open(_ todo: HomeTodo) {
        switch onEnum(of: todo) {
        case .rejectedProof:
            let bills = billsStore ?? session.makeBillsStore()
            billsStore = bills
            resubmit = ResubmitSheet(store: bills)
        case .dues:
            selection = 1
        case .lunchVote:
            selection = 2
        case .supportReply(let reply):
            // One waiting ticket opens its thread; two or more open the list.
            onOpenSupport(reply.count == 1 ? reply.ticketId?.int64Value : nil)
        }
    }

    // MARK: All clear

    private var allClearCard: some View {
        HStack(spacing: 13) {
            Circle().fill(Pent.okBg).frame(width: 46, height: 46)
                .overlay(Image(systemName: "party.popper.fill").font(.system(size: 22)).foregroundStyle(Pent.ok))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("You're all clear").font(.pentHeadline).foregroundStyle(Pent.label)
                Text("No dues, no votes waiting. Nice.").font(.system(size: secondarySize)).foregroundStyle(Pent.label2)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .pentGlass(20)
        .accessibilityElement(children: .combine)
    }

    // MARK: Coming up

    @ViewBuilder
    private func comingUp(_ sections: HomeSections) -> some View {
        let lunch = sections.comingUpLunch
        let activity = sections.comingUpActivity
        let openCount = Int(sections.openActivitiesCount)
        let hasRows = sections.proofInReview != nil || lunch != nil || activity != nil

        SectionHeader(title: "Coming up")
        if !hasRows && openCount == 0 {
            emptyComingUp
        } else {
            VStack(spacing: 0) {
                if let amount = sections.proofInReview {
                    proofInReviewRow(amount)
                }
                if let lunch {
                    if sections.proofInReview != nil { HomeRowDivider() }
                    lunchRow(lunch)
                }
                if let activity {
                    if sections.proofInReview != nil || lunch != nil { HomeRowDivider() }
                    activityRow(activity)
                }
                if openCount > 0 {
                    if hasRows { HomeRowDivider() }
                    openToJoinRow(openCount)
                }
            }
            .pentGlass(20)
        }
    }

    private func proofInReviewRow(_ amount: String) -> some View {
        ComingUpRow(title: "Proof in review · MYR \(amount)", action: { selection = 1 }) {
            DomainIcon(symbol: "clock.fill", tint: Pent.proof, bg: Pent.proofBg, size: 36, corner: 11, iconSize: 17)
        } detail: {
            Text("Dues clear once the committee approves")
                .font(.system(size: secondarySize)).foregroundStyle(Pent.label2).lineLimit(1)
        }
    }

    private func lunchRow(_ lunch: DashboardLunchDto) -> some View {
        ComingUpRow(title: lunchTitle(lunch), action: { selection = 2 }) {
            DateChip(weekday: PentDates.weekday(ymd: lunch.date) ?? "", day: PentDates.dayNumber(ymd: lunch.date) ?? "", kind: .lunch)
        } detail: {
            switch dashboardLunchStatus(lunch: lunch) {
            case .responded: HomePill(text: "Voted")
            case .closed: HomePill(text: "Closed", color: Pent.neutral, bg: Pent.neutralBg)
            // An open, unvoted next lunch is a to-do; this only shows for an open other_lunch.
            case .voteNow: HomePill(text: "Vote now", color: Pent.warn, bg: Pent.warnBg)
            }
        }
    }

    @ViewBuilder
    private func activityRow(_ activity: DashboardActivityDto) -> some View {
        let start = activity.startsAt.flatMap { PentDates.date(iso: $0) }
        ComingUpRow(title: activity.title, action: { selection = 3 }) {
            if let start {
                DateChip(weekday: PentDates.weekday(start), day: PentDates.dayNumber(start), kind: .activity)
            } else {
                DomainIcon(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg, size: 36, corner: 11, iconSize: 17)
            }
        } detail: {
            HStack(spacing: 6) {
                // next_activity is always one of the member's registrations, so anything
                // non-waitlisted renders as Registered.
                if dashboardActivityStatus(activity: activity) == .waitlisted {
                    HomePill(text: "Waitlisted", color: Pent.warn, bg: Pent.warnBg)
                } else {
                    HomePill(text: "Registered")
                }
                if let start {
                    Text(PentDates.time(start)).font(.system(size: secondarySize)).foregroundStyle(Pent.label2).lineLimit(1)
                }
            }
        }
    }

    private func openToJoinRow(_ count: Int) -> some View {
        Button { selection = 3 } label: {
            HStack(spacing: 8) {
                Image(systemName: "sparkles").accessibilityHidden(true)
                Text(count == 1 ? "1 activity open to join" : "\(count) activities open to join")
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HomeChevron()
            }
            .font(.system(size: openToJoinSize, weight: .semibold))
            .foregroundStyle(Pent.okStrong)
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var emptyComingUp: some View {
        VStack(spacing: 2) {
            Text("Nothing coming up")
                .font(.system(size: emptyTitleSize, weight: .semibold)).foregroundStyle(Pent.label)
            Text("No lunches or activities you've joined yet.")
                .font(.system(size: secondarySize)).foregroundStyle(Pent.label2)
                .multilineTextAlignment(.center)
            Button { selection = 3 } label: { CueLabel(text: "Browse activities") }
                .buttonStyle(.plain)
                .padding(.top, 10)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
        .padding(.horizontal, 16)
        .pentGlass(20)
    }

    // MARK: Ask the committee

    private var askCommitteeRow: some View {
        ComingUpRow(title: "Ask the committee", action: { onOpenSupport(nil) }) {
            DomainIcon(symbol: "lifepreserver.fill", tint: Pent.accent, bg: Pent.lunchBg, size: 36, corner: 11, iconSize: 17)
        } detail: {
            Text(supportCardSubtitle(waiting: 0))
                .font(.system(size: secondarySize)).foregroundStyle(Pent.label2).lineLimit(1)
        }
        .pentGlass(20)
    }

    // MARK: Format

    private var todayString: String {
        let formatter = DateFormatter(); formatter.dateFormat = "EEEE, d MMMM"
        return formatter.string(from: Date())
    }

    /// Shared with Android: "Lunch · Nasi lemak · Kak Mah" (menu, else caterer), or "Lunch".
    private func lunchTitle(_ lunch: DashboardLunchDto) -> String {
        dashboardLunchLabel(lunch: lunch)
    }
}

// MARK: - Shared date formatting

enum PentDates {
    /// "2026-10-30" -> "Fri"
    static func weekday(ymd: String) -> String? {
        ymdDate(ymd).map { weekday($0) }
    }
    /// "2026-10-30" -> "30"
    static func dayNumber(ymd: String) -> String? {
        ymdDate(ymd).map { dayNumber($0) }
    }
    /// ISO8601 -> weekday in the device's time zone ("Thu")
    static func weekday(iso: String) -> String? {
        date(iso: iso).map { weekday($0) }
    }
    static func weekday(_ date: Date) -> String {
        let formatter = DateFormatter(); formatter.dateFormat = "EEE"
        return formatter.string(from: date)
    }
    static func dayNumber(_ date: Date) -> String {
        let formatter = DateFormatter(); formatter.dateFormat = "d"
        return formatter.string(from: date)
    }
    /// Time of day in the device's time zone ("8:00 PM")
    static func time(_ date: Date) -> String {
        let formatter = DateFormatter(); formatter.dateFormat = "h:mm a"
        return formatter.string(from: date)
    }
    /// ISO8601, with or without fractional seconds.
    static func date(iso: String) -> Date? {
        let parser = ISO8601DateFormatter(); parser.formatOptions = [.withInternetDateTime]
        if let date = parser.date(from: iso) { return date }
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return parser.date(from: iso)
    }
    private static func ymdDate(_ ymd: String) -> Date? {
        let parser = DateFormatter(); parser.dateFormat = "yyyy-MM-dd"; parser.locale = Locale(identifier: "en_US_POSIX")
        return parser.date(from: ymd)
    }
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
        // A just-written timestamp can be a moment ahead of this device's clock, and the formatter
        // words a zero or future gap as "in 0 sec"; anything under a minute old is just "now".
        let now = Date()
        if now.timeIntervalSince(date) < 60 { return "now" }
        return formatter.localizedString(for: date, relativeTo: now)
    }
}
