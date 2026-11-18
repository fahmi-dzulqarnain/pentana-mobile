//
//  ActivitiesView.swift
//  iosApp
//
//  Upcoming activities: register (with the activity's questions), waitlist
//  position, or cancel. Cards over the ambient field.
//

@preconcurrency import Shared
import SwiftUI

struct ActivitiesView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: ActivitiesStore?
    @State private var state: ActivitiesUiState = ActivitiesUiStateLoading.shared
    @State private var inFlight: Set<Int64> = []
    @State private var actionError: String?
    @State private var registering: ActivityDto?
    @State private var openedActivityId: Int64?

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeActivitiesStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                async let flights: Void = { for await ids in activeStore.inFlight { await MainActor.run { inFlight = Set(ids.map { $0.int64Value }) } } }()
                async let errors: Void = { for await message in activeStore.actionError { await MainActor.run { actionError = message } } }()
                _ = await (states, flights, errors)
            }
            .alert(
                "Something went wrong",
                // The detail screen shows its own alert while it's on top.
                isPresented: Binding(get: { actionError != nil && openedActivityId == nil },
                                     set: { if !$0 { actionError = nil; store?.dismissActionError() } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(actionError ?? "")
            }
            .sheet(item: $registering, onDismiss: { store?.resetReg() }) { activity in
                if let store {
                    RegisterActivityView(store: store, activity: activity)
                }
            }
            .navigationDestination(item: $openedActivityId) { activityId in
                if let store {
                    ActivityDetailView(store: store, activityId: activityId)
                }
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                               title: "Couldn't load", message: error.message,
                               actionTitle: "Try again", action: { store?.load() })
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            ScrollView {
                if content.activities.isEmpty {
                    EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                                   title: "No upcoming activities", message: "Check back soon for events to join.")
                        .containerRelativeFrame(.vertical, alignment: .center)
                } else {
                    VStack(spacing: 13) {
                        ForEach(content.activities, id: \.id) { activity in
                            ActivityCard(activity: activity, busy: inFlight.contains(activity.id),
                                         onOpen: { openedActivityId = activity.id },
                                         onRegister: {
                                             if activity.questions.isEmpty {
                                                 store?.quickRegister(activityId: activity.id)
                                             } else {
                                                 store?.resetReg()
                                                 registering = activity
                                             }
                                         },
                                         onCancel: { store?.cancel(activityId: activity.id) })
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
            .refreshable { try? await store?.refresh() }
        }
    }
}

extension ActivityDto: @retroactive Identifiable {}

private struct ActivityCard: View {
    let activity: ActivityDto
    let busy: Bool
    let onOpen: () -> Void
    let onRegister: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                Text(activity.title).font(.pentHeadline).foregroundStyle(Pent.label)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ActivitySpotsPill(activity: activity)
            }

            VStack(alignment: .leading, spacing: 3) {
                if let when = activity.startsAt.flatMap(PentDates.dateTime) {
                    Label(when, systemImage: "calendar").labelStyle(IconLeading(tint: Pent.activ))
                }
                if let location = activity.location, !location.isEmpty {
                    Label(location, systemImage: "mappin.and.ellipse").labelStyle(IconLeading(tint: Pent.activ))
                }
            }
            .font(.pentFoot).foregroundStyle(Pent.label2)
            .padding(.top, 6)

            if let overview = activityOverview(activity: activity) {
                Text(overview).font(.pentFoot).foregroundStyle(Pent.label2).lineLimit(3)
                    .padding(.top, 8)
            }

            ActivityActionView(activity: activity, onRegister: onRegister, onCancel: onCancel)
                .padding(.top, 13)
                .allowsHitTesting(!busy)
        }
        .padding(16)
        .background(Pent.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
        .opacity(busy ? 0.6 : 1)
        // Tapping anywhere outside the action buttons opens the detail screen.
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onTapGesture(perform: onOpen)
        .accessibilityAction(named: "Show details", onOpen)
    }
}

/// The register / cancel / waitlist row, shared by the card and the detail screen.
struct ActivityActionView: View {
    let activity: ActivityDto
    let onRegister: () -> Void
    let onCancel: () -> Void

    private var cardState: ActivityCardState { activityCardState(activity: activity) }

    var body: some View { action }

    @ViewBuilder private var action: some View {
        switch cardState {
        case .registered:
            HStack {
                Label("You're registered", systemImage: "checkmark.circle.fill")
                    .font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.ok).labelStyle(.titleAndIcon)
                Spacer()
                cancelButton
            }
        case .waitlisted:
            HStack {
                Label(waitlistLabel(activity: activity), systemImage: "clock.fill")
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
}

/// Capacity / status pill, shared by the card and the detail screen.
struct ActivitySpotsPill: View {
    let activity: ActivityDto

    var body: some View {
        let cardState = activityCardState(activity: activity)
        let (label, color, bg): (String, Color, Color) = {
            switch cardState {
            case .registered: return ("Registered", Pent.ok, Pent.okBg)
            case .waitlisted: return ("Full", Pent.warn, Pent.warnBg)
            case .closed: return ("Closed", Pent.neutral, Pent.neutralBg)
            case .open: return (spotsLabel(activity: activity), Pent.activ, Pent.activBg)
            }
        }()
        return StatusPill(label, color: color, bg: bg)
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
