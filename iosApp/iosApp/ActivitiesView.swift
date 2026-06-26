//
//  ActivitiesView.swift
//  iosApp
//
//  Upcoming activities: register (with the activity's questions), waitlist
//  position, or cancel. Cards over the ambient field.
//

import Shared
import SwiftUI

struct ActivitiesView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var activities: [ActivityDto] = []
    @State private var isLoading = true
    @State private var busyId: Int64?
    @State private var registering: ActivityDto?

    var body: some View {
        ScrollView {
            VStack(spacing: 13) {
                if activities.isEmpty && !isLoading {
                    EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                                   title: "No upcoming activities", message: "Check back soon for events to join.")
                }
                ForEach(activities, id: \.id) { activity in
                    ActivityCard(activity: activity, busy: busyId == activity.id,
                                 onRegister: {
                                     if activity.questions.isEmpty {
                                         Task { await register(activity, answers: [:]) }
                                     } else {
                                         registering = activity
                                     }
                                 },
                                 onCancel: { Task { await cancel(activity) } })
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
        .overlay { if isLoading && activities.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $registering) { activity in
            RegisterActivityView(activity: activity) { updated in replace(updated) }
                .environmentObject(session)
        }
    }

    private func register(_ activity: ActivityDto, answers: [String: String]) async {
        busyId = activity.id
        do { replace(try await session.activities.register(activityId: activity.id, answers: answers)) } catch {}
        busyId = nil
    }
    private func cancel(_ activity: ActivityDto) async {
        busyId = activity.id
        do { replace(try await session.activities.cancel(activityId: activity.id)) } catch {}
        busyId = nil
    }
    private func replace(_ updated: ActivityDto) {
        if let i = activities.firstIndex(where: { $0.id == updated.id }) { activities[i] = updated }
    }
    private func load() async {
        isLoading = true
        do { activities = try await session.activities.activities() } catch {}
        isLoading = false
    }
}

extension ActivityDto: @retroactive Identifiable {}

private struct ActivityCard: View {
    let activity: ActivityDto
    let busy: Bool
    let onRegister: () -> Void
    let onCancel: () -> Void

    private enum State { case registered, waitlisted, open, closed }
    private var state: State {
        switch activity.myStatus {
        case "registered": return .registered
        case "waitlisted": return .waitlisted
        default: return activity.isOpen ? .open : .closed
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                Text(activity.title).font(.pentHeadline).foregroundStyle(Pent.label)
                    .frame(maxWidth: .infinity, alignment: .leading)
                spotsPill
            }

            VStack(alignment: .leading, spacing: 3) {
                if let when = activity.startsAt.flatMap(PentDates.dateTime) {
                    Label(when, systemImage: "calendar").labelStyle(IconLeading(tint: Pent.activ))
                }
                if let where_ = activity.location, !where_.isEmpty {
                    Label(where_, systemImage: "mappin.and.ellipse").labelStyle(IconLeading(tint: Pent.activ))
                }
            }
            .font(.pentFoot).foregroundStyle(Pent.label2)
            .padding(.top, 6)

            if let blurb = plainText(activity.description_) {
                Text(blurb).font(.pentFoot).foregroundStyle(Pent.label2).lineLimit(3)
                    .padding(.top, 8)
            }

            action.padding(.top, 13)
        }
        .padding(16)
        .background(Pent.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
        .opacity(busy ? 0.6 : 1)
        .allowsHitTesting(!busy)
    }

    @ViewBuilder private var action: some View {
        switch state {
        case .registered:
            HStack {
                Label("You're registered", systemImage: "checkmark.circle.fill")
                    .font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.ok).labelStyle(.titleAndIcon)
                Spacer()
                cancelButton
            }
        case .waitlisted:
            HStack {
                Label(waitlistText, systemImage: "clock.fill")
                    .font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.warn).labelStyle(.titleAndIcon)
                Spacer()
                cancelButton
            }
        case .closed:
            Label("Registration closed", systemImage: "lock.fill")
                .font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.label3).labelStyle(.titleAndIcon)
        case .open:
            Button("Register", action: onRegister)
                .buttonStyle(PentProminentButtonStyle())
        }
    }

    private var cancelButton: some View {
        Button(role: .destructive, action: onCancel) {
            Text("Cancel").font(.pentSub).fontWeight(.semibold)
        }
        .tint(Pent.bad)
    }

    private var spotsPill: some View {
        let (label, color, bg): (String, Color, Color) = {
            switch state {
            case .registered: return ("Registered", Pent.ok, Pent.okBg)
            case .waitlisted: return ("Full", Pent.warn, Pent.warnBg)
            case .closed: return ("Closed", Pent.neutral, Pent.neutralBg)
            case .open:
                if let n = activity.spotsLeft?.int32Value {
                    return ("\(n) spot\(n == 1 ? "" : "s") left", Pent.activ, Pent.activBg)
                }
                return ("Open", Pent.activ, Pent.activBg)
            }
        }()
        return StatusPill(label, color: color, bg: bg)
    }

    private var waitlistText: String {
        if let pos = activity.waitlistPosition?.int32Value { return "Waitlisted — #\(pos) in line" }
        return "Waitlisted"
    }

    private func plainText(_ html: String?) -> String? {
        guard let html, !html.isEmpty else { return nil }
        let s = html
            .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty ? nil : s
    }
}

/// Small leading-icon label style tinted independently of the text.
struct IconLeading: LabelStyle {
    var tint: Color
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 6) {
            configuration.icon.foregroundStyle(tint).font(.system(size: 13))
            configuration.title
        }
    }
}
