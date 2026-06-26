//
//  ActivitiesView.swift
//  iosApp
//
//  Upcoming activities: register (with the activity's questions), see waitlist
//  position, or cancel. Mirrors the Bills/Lunch tabs.
//

import Shared
import SwiftUI

struct ActivitiesView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var activities: [ActivityDto] = []
    @State private var isLoading = true
    @State private var busyId: Int64?
    @State private var registering: ActivityDto? // drives the question sheet

    var body: some View {
        List {
            if activities.isEmpty && !isLoading {
                Text("No upcoming activities.").foregroundStyle(.secondary)
            }
            ForEach(activities, id: \.id) { activity in
                Section {
                    header(activity)
                    actions(activity)
                }
            }
        }
        .overlay { if isLoading && activities.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $registering) { activity in
            RegisterActivityView(activity: activity) { updated in replace(updated) }
                .environmentObject(session)
        }
    }

    // MARK: - Rows

    @ViewBuilder
    private func header(_ activity: ActivityDto) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(activity.title).font(.headline)
            if let when = formattedStart(activity.startsAt) {
                Label(when, systemImage: "calendar").font(.caption).foregroundStyle(.secondary)
            }
            if let location = activity.location, !location.isEmpty {
                Label(location, systemImage: "mappin.and.ellipse").font(.caption).foregroundStyle(.secondary)
            }
            if let blurb = plainText(activity.description_) {
                Text(blurb).font(.caption).foregroundStyle(.secondary).lineLimit(3)
            }
            if let spots = activity.spotsLeft?.int32Value {
                Text("\(spots) spot\(spots == 1 ? "" : "s") left")
                    .font(.caption2).foregroundStyle(spots > 0 ? .green : .orange)
            }
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func actions(_ activity: ActivityDto) -> some View {
        let busy = busyId == activity.id
        switch activity.myStatus {
        case "registered":
            Label("You're registered", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green).font(.subheadline)
            cancelButton(activity, busy: busy)
        case "waitlisted":
            Label(waitlistText(activity), systemImage: "clock.fill")
                .foregroundStyle(.orange).font(.subheadline)
            cancelButton(activity, busy: busy)
        default:
            if activity.isOpen {
                Button {
                    if activity.questions.isEmpty {
                        Task { await register(activity, answers: [:]) }
                    } else {
                        registering = activity
                    }
                } label: {
                    Label("Register", systemImage: "plus.circle")
                }
                .disabled(busy)
            } else {
                Text("Registration closed").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func cancelButton(_ activity: ActivityDto, busy: Bool) -> some View {
        Button(role: .destructive) {
            Task { await cancel(activity) }
        } label: {
            Label("Cancel registration", systemImage: "xmark.circle")
        }
        .disabled(busy)
    }

    private func waitlistText(_ activity: ActivityDto) -> String {
        if let pos = activity.waitlistPosition?.int32Value {
            return "Waitlisted — #\(pos) in line"
        }
        return "Waitlisted"
    }

    // MARK: - Actions

    private func register(_ activity: ActivityDto, answers: [String: String]) async {
        busyId = activity.id
        do {
            let updated = try await session.activities.register(activityId: activity.id, answers: answers)
            replace(updated)
        } catch {
            // Keep current state; a banner could be added later.
        }
        busyId = nil
    }

    private func cancel(_ activity: ActivityDto) async {
        busyId = activity.id
        do {
            let updated = try await session.activities.cancel(activityId: activity.id)
            replace(updated)
        } catch {
            // Keep current state.
        }
        busyId = nil
    }

    private func replace(_ updated: ActivityDto) {
        if let index = activities.firstIndex(where: { $0.id == updated.id }) {
            activities[index] = updated
        }
    }

    private func load() async {
        isLoading = true
        do {
            activities = try await session.activities.activities()
        } catch {
            // Keep what we have.
        }
        isLoading = false
    }

    // MARK: - Formatting

    private func formattedStart(_ iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime]
        guard let date = parser.date(from: iso) else { return iso }
        let out = DateFormatter()
        out.dateFormat = "EEE, d MMM yyyy • h:mm a"
        return out.string(from: date)
    }

    /// The description is rich-text HTML; show a stripped plain-text preview in the list.
    private func plainText(_ html: String?) -> String? {
        guard let html, !html.isEmpty else { return nil }
        let stripped = html
            .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return stripped.isEmpty ? nil : stripped
    }
}

// Kotlin data classes don't conform to Identifiable; `id` (Int64) satisfies it for `.sheet(item:)`.
extension ActivityDto: @retroactive Identifiable {}
