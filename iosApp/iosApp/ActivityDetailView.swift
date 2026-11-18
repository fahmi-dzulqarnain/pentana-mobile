//
//  ActivityDetailView.swift
//  iosApp
//
//  One activity: title, when and where, the full description (text, links,
//  images), and the same register / cancel / waitlist action as its card.
//  Follows the shared ActivitiesStore, so an action here updates the list too.
//

@preconcurrency import Shared
import SwiftUI

struct ActivityDetailView: View {
    let store: ActivitiesStore
    let activityId: Int64

    @State private var state: ActivitiesUiState = ActivitiesUiStateLoading.shared
    @State private var inFlight: Set<Int64> = []
    @State private var actionError: String?
    @State private var registering: ActivityDto?

    private var activity: ActivityDto? { findActivity(state: state, activityId: activityId) }

    var body: some View {
        content
            .background(AmbientBackground())
            .navigationTitle("Activity")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                async let states: Void = { for await value in store.state { await MainActor.run { state = value } } }()
                async let flights: Void = { for await ids in store.inFlight { await MainActor.run { inFlight = Set(ids.map { $0.int64Value }) } } }()
                async let errors: Void = { for await message in store.actionError { await MainActor.run { actionError = message } } }()
                _ = await (states, flights, errors)
            }
            .alert(
                "Something went wrong",
                isPresented: Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil; store.dismissActionError() } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(actionError ?? "")
            }
            .sheet(item: $registering, onDismiss: { store.resetReg() }) { activity in
                RegisterActivityView(store: store, activity: activity)
            }
    }

    @ViewBuilder private var content: some View {
        if let activity {
            ScrollView {
                details(activity)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 28)
            }
            .safeAreaInset(edge: .bottom) { actionBar(activity) }
        } else if case .error(let error) = onEnum(of: state) {
            EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                           title: "Couldn't load", message: error.message,
                           actionTitle: "Try again", action: { store.load() })
        } else if case .loading = onEnum(of: state) {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                           title: "Activity not found", message: "It may have been removed. Go back to see the latest list.")
        }
    }

    private func details(_ activity: ActivityDto) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                Text(activity.title).font(.pentTitle2).foregroundStyle(Pent.label)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ActivitySpotsPill(activity: activity)
            }

            VStack(alignment: .leading, spacing: 5) {
                if let when = activity.startsAt.flatMap(PentDates.dateTime) {
                    Label(when, systemImage: "calendar").labelStyle(IconLeading(tint: Pent.activ))
                }
                if let location = activity.location, !location.isEmpty {
                    Label(location, systemImage: "mappin.and.ellipse").labelStyle(IconLeading(tint: Pent.activ))
                }
            }
            .font(.pentSub).foregroundStyle(Pent.label2)
            .padding(.top, 10)

            let blocks = activityDetailBlocks(activity: activity)
            if !blocks.isEmpty {
                Divider().overlay(Pent.separator).padding(.vertical, 18)
                RichTextView(blocks: blocks)
            }
        }
    }

    private func actionBar(_ activity: ActivityDto) -> some View {
        let busy = inFlight.contains(activity.id)
        return ActivityActionView(
            activity: activity,
            onRegister: {
                if activity.questions.isEmpty {
                    store.quickRegister(activityId: activity.id)
                } else {
                    store.resetReg()
                    registering = activity
                }
            },
            onCancel: { store.cancel(activityId: activity.id) }
        )
        .opacity(busy ? 0.6 : 1)
        .allowsHitTesting(!busy)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.bar)
    }
}

/// An activity description drawn natively from the shared `RichBlock`s: `Pent` type and colours,
/// links that open in Safari (SwiftUI's default `openURL`), images fitted to the width.
struct RichTextView: View {
    let blocks: [any RichBlock]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch onEnum(of: block) {
                case .paragraph(let paragraph):
                    paragraphText(paragraph.spans)
                case .listItem(let item):
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(item.marker).font(.pentBody).foregroundStyle(Pent.label2)
                            .frame(minWidth: 16, alignment: .leading)
                        paragraphText(item.spans)
                    }
                    .padding(.leading, CGFloat(item.level - 1) * 16)
                case .image(let image):
                    DescriptionImage(url: URL(string: image.url))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func paragraphText(_ spans: [RichSpan]) -> some View {
        Text(Self.attributed(spans))
            .font(.pentBody)
            .foregroundStyle(Pent.label)
            .tint(Pent.accent)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    static func attributed(_ spans: [RichSpan]) -> AttributedString {
        var result = AttributedString()
        for span in spans {
            var piece = AttributedString(span.text)
            var font = Font.pentBody
            if span.bold { font = font.weight(.semibold) }
            if span.italic { font = font.italic() }
            piece.font = font
            if let link = span.link, let url = URL(string: link) {
                piece.link = url
                piece.underlineStyle = .single
            }
            result += piece
        }
        return result
    }
}

private struct DescriptionImage: View {
    let url: URL?

    var body: some View {
        AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFit()
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            case .failure:
                Label("Image unavailable", systemImage: "photo")
                    .font(.pentSub).foregroundStyle(Pent.label2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(Pent.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            default:
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Pent.surface2)
                    .frame(height: 180)
                    .overlay(ProgressView())
            }
        }
        .accessibilityHidden(true)
    }
}
