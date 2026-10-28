//
//  HomeComponents.swift
//  iosApp
//
//  Building blocks for the Home "Needs you" / "Coming up" layout: the to-do row (one tap
//  target, 2 lines max, cue button), coming-up rows with date chips, section headers and
//  the loading skeleton.
//

@preconcurrency import Shared
import SwiftUI

// MARK: - To-do row

/// Display values for one "Needs you" row. Built from a shared `HomeTodo` by `TodoRowModel(todo:)`.
struct TodoRowModel {
    var symbol: String
    var tint: Color
    var bg: Color
    var title: String
    /// Dues title: rounded money figure in the dues colour.
    var titleIsMoney: Bool = false
    var detail: String
    var cue: String
}

extension TodoRowModel {
    init(todo: HomeTodo) {
        switch onEnum(of: todo) {
        case .rejectedProof(let proof):
            var detail = "MYR \(proof.amount)"
            if let reason = proof.reason?.trimmingCharacters(in: .whitespacesAndNewlines), !reason.isEmpty {
                detail += " · \(reason)"
            }
            self.init(symbol: "exclamationmark.triangle.fill", tint: Pent.bad, bg: Pent.badBg,
                      title: "Proof rejected", detail: detail, cue: "Resubmit")
        case .dues(let dues):
            var parts: [String] = []
            let unpaid = Int(dues.unpaidCount)
            if unpaid > 0 { parts.append(unpaid == 1 ? "1 bill" : "\(unpaid) bills") }
            if let inReview = dues.inReview {
                parts.append("MYR \(inReview) in review")
            } else if (Double(dues.credit) ?? 0) > 0 {
                parts.append("MYR \(dues.credit) credit")
            }
            self.init(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg,
                      title: "MYR \(dues.amountDue) due", titleIsMoney: true,
                      detail: parts.joined(separator: " · "), cue: "Pay & upload")
        case .lunchVote(let vote):
            let lunch = vote.lunch
            var parts: [String] = []
            let names = lunch.menus.compactMap { $0.menu ?? $0.caterer }.filter { !$0.isEmpty }
            if lunch.menus.count > 1 {
                parts.append("\(lunch.menus.count) menus")
            } else if let name = names.first ?? lunch.menu, !name.isEmpty {
                parts.append(name)
            }
            if let deadline = lunch.voteDeadline, let day = PentDates.weekday(iso: deadline) {
                parts.append("closes \(day)")
            }
            let day = PentDates.weekday(ymd: lunch.date) ?? PentDates.shortDate(lunch.date)
            self.init(symbol: "fork.knife", tint: Pent.lunch, bg: Pent.lunchBg,
                      title: "Vote for \(day) lunch",
                      detail: parts.isEmpty ? "Voting is open" : parts.joined(separator: " · "), cue: "Vote")
        case .supportReply(let reply):
            let count = Int(reply.count)
            let detail: String
            if count == 1, let subject = reply.subject, !subject.isEmpty {
                detail = subject
            } else {
                detail = count == 1 ? "1 ticket waiting on you" : "\(count) tickets waiting on you"
            }
            self.init(symbol: "bubble.left.and.text.bubble.right.fill", tint: Pent.proof, bg: Pent.proofBg,
                      title: "Committee replied", detail: detail, cue: "Reply")
        }
    }
}

/// One "Needs you" row: icon tile, title + detail (1 line each), and a cue button. The whole row
/// is a single button; the cue is visual only. At accessibility text sizes the cue moves below the
/// text and the line limit is lifted.
struct TodoRow: View {
    let model: TodoRowModel
    let action: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .subheadline) private var titleSize: CGFloat = 15
    @ScaledMetric(relativeTo: .subheadline) private var moneySize: CGFloat = 16
    @ScaledMetric(relativeTo: .footnote) private var detailSize: CGFloat = 13
    @ScaledMetric(relativeTo: .caption) private var cueSize: CGFloat = 12.5

    private var large: Bool { typeSize >= .accessibility1 }

    var body: some View {
        Button(action: action) {
            Group {
                if large {
                    HStack(alignment: .top, spacing: 11) {
                        icon
                        VStack(alignment: .leading, spacing: 10) {
                            texts
                            cue
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.vertical, 12)
                } else {
                    HStack(spacing: 11) {
                        icon
                        texts.frame(maxWidth: .infinity, alignment: .leading)
                        cue
                    }
                    .padding(.vertical, 8)
                }
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 62, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }

    private var icon: some View {
        DomainIcon(symbol: model.symbol, tint: model.tint, bg: model.bg, size: 36, corner: 11, iconSize: 17)
            .accessibilityHidden(true)
    }

    private var texts: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(model.title)
                .font(model.titleIsMoney ? .pentMoney(moneySize) : .system(size: titleSize, weight: .semibold))
                .foregroundStyle(model.titleIsMoney ? Pent.dues : Pent.label)
                .lineLimit(large ? nil : 1)
                .truncationMode(.tail)
            Text(model.detail)
                .font(.system(size: detailSize))
                .foregroundStyle(Pent.label2)
                .lineLimit(large ? nil : 1)
                .truncationMode(.tail)
        }
    }

    private var cue: some View {
        CueLabel(text: model.cue, size: cueSize)
    }
}

/// The indigo (light) / light-orange (dark) capsule used as a to-do cue and the empty-state button.
struct CueLabel: View {
    let text: String
    var size: CGFloat = 12.5

    var body: some View {
        Text(text)
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(Pent.ctaFg)
            .lineLimit(1)
            .padding(.vertical, 8)
            .padding(.horizontal, 14)
            .background(Pent.ctaBg, in: Capsule())
            .fixedSize()
    }
}

// MARK: - Coming up row

/// A "Coming up" row: leading chip or icon, a 1-line title, a detail line, and a chevron.
struct ComingUpRow<Leading: View, Detail: View>: View {
    let title: String
    let action: () -> Void
    @ViewBuilder var leading: Leading
    @ViewBuilder var detail: Detail

    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .subheadline) private var titleSize: CGFloat = 15

    var body: some View {
        Button(action: action) {
            HStack(spacing: 11) {
                leading
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.system(size: titleSize, weight: .semibold))
                        .foregroundStyle(Pent.label)
                        .lineLimit(typeSize >= .accessibility1 ? nil : 1)
                    detail
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HomeChevron()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 62, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}

struct HomeChevron: View {
    var body: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(Pent.label2.opacity(0.6))
            .accessibilityHidden(true)
    }
}

/// Small status pill for Coming up rows; green pills use the stronger green text.
struct HomePill: View {
    let text: String
    var color: Color = Pent.okStrong
    var bg: Color = Pent.okBg

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(color)
            .padding(.vertical, 2)
            .padding(.horizontal, 8)
            .background(bg, in: Capsule())
            .lineLimit(1)
            .fixedSize()
    }
}

// MARK: - Date chip

/// A 40pt-wide calendar tile: a coloured weekday strip over the day number, on the surface colour.
struct DateChip: View {
    enum Kind { case lunch, activity }

    let weekday: String
    let day: String
    let kind: Kind

    var body: some View {
        VStack(spacing: 0) {
            Text(weekday.uppercased())
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(kind == .lunch ? Color(hex: 0x14121F) : Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 2)
                .background(kind == .lunch ? Pent.orange : Color(hex: 0x0B6E42))
            Text(day)
                .font(.pentMoney(17))
                .foregroundStyle(Pent.label)
                .padding(.top, 1)
                .padding(.bottom, 3)
        }
        .frame(width: 40)
        .background(Pent.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .shadow(color: .black.opacity(0.1), radius: 1.5, y: 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(weekday) \(day)")
    }
}

// MARK: - Section header

/// 15pt semibold, sentence case, primary label; an optional count in secondary text.
struct SectionHeader: View {
    let title: String
    var count: Int? = nil

    @ScaledMetric(relativeTo: .subheadline) private var titleSize: CGFloat = 15
    @ScaledMetric(relativeTo: .footnote) private var countSize: CGFloat = 13

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(title).font(.system(size: titleSize, weight: .semibold)).foregroundStyle(Pent.label)
            if let count {
                Text("\(count)").font(.system(size: countSize, weight: .medium)).foregroundStyle(Pent.label2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .padding(.top, 14)
        .padding(.bottom, 7)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// Full-width hairline between rows inside a Home section surface.
struct HomeRowDivider: View {
    var body: some View { PentHairline(leadingInset: 0) }
}

// MARK: - Loading skeleton

/// Placeholder for "Needs you" and "Coming up" (two rows each), shown under the real greeting.
struct HomeSkeleton: View {
    private let fill = Color(light: 0x3C375A, dark: 0xFFFFFF, lightAlpha: 0.08, darkAlpha: 0.07)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            bar(width: 0.4).padding(.top, 14).padding(.bottom, 8)
            group(leadingWidth: 36, leadingHeight: 36, titleWidth: 0.7, detailWidth: 0.45)
            bar(width: 0.35).padding(.top, 18).padding(.bottom, 8)
            group(leadingWidth: 40, leadingHeight: 42, titleWidth: 0.6, detailWidth: 0.3)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading")
    }

    private func bar(width: CGFloat) -> some View {
        GeometryReader { proxy in
            RoundedRectangle(cornerRadius: 8, style: .continuous).fill(fill)
                .frame(width: proxy.size.width * width, height: 14)
        }
        .frame(height: 14)
        .padding(.horizontal, 4)
    }

    private func group(leadingWidth: CGFloat, leadingHeight: CGFloat, titleWidth: CGFloat, detailWidth: CGFloat) -> some View {
        VStack(spacing: 0) {
            row(leadingWidth: leadingWidth, leadingHeight: leadingHeight, titleWidth: titleWidth, detailWidth: detailWidth)
            HomeRowDivider()
            row(leadingWidth: leadingWidth, leadingHeight: leadingHeight, titleWidth: titleWidth, detailWidth: detailWidth)
        }
        .pentGlass(20)
    }

    private func row(leadingWidth: CGFloat, leadingHeight: CGFloat, titleWidth: CGFloat, detailWidth: CGFloat) -> some View {
        HStack(spacing: 11) {
            RoundedRectangle(cornerRadius: 8, style: .continuous).fill(fill)
                .frame(width: leadingWidth, height: leadingHeight)
            GeometryReader { proxy in
                VStack(alignment: .leading, spacing: 7) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous).fill(fill)
                        .frame(width: proxy.size.width * titleWidth, height: 12)
                    RoundedRectangle(cornerRadius: 6, style: .continuous).fill(fill)
                        .frame(width: proxy.size.width * detailWidth, height: 10)
                }
                .frame(maxHeight: .infinity, alignment: .center)
            }
            .frame(height: 29)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 62)
    }
}

// MARK: - Previews

private enum TodoPreviewData {
    static let rows: [TodoRowModel] = [
        TodoRowModel(todo: HomeTodoRejectedProof(proofId: 1, amount: "60.00", reason: "Blurry photo, please retake it in better light")),
        TodoRowModel(todo: HomeTodoDues(amountDue: "120.00", unpaidCount: 2, credit: "15.00", inReview: nil)),
        TodoRowModel(symbol: "fork.knife", tint: Pent.lunch, bg: Pent.lunchBg,
                     title: "Vote for Fri lunch", detail: "2 menus · closes Thu", cue: "Vote"),
        TodoRowModel(todo: HomeTodoSupportReply(count: 1, ticketId: KotlinLong(value: 7), subject: "Can I pay the October dues in two parts?")),
    ]
}

private struct TodoRowsPreview: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(Array(TodoPreviewData.rows.enumerated()), id: \.offset) { index, model in
                    if index > 0 { HomeRowDivider() }
                    TodoRow(model: model) {}
                }
            }
            .pentGlass(20)
            .padding(16)
        }
        .background(AmbientBackground())
    }
}

#Preview("TodoRow · light") {
    TodoRowsPreview().preferredColorScheme(.light)
}

#Preview("TodoRow · dark") {
    TodoRowsPreview().preferredColorScheme(.dark)
}

#Preview("TodoRow · accessibility2") {
    TodoRowsPreview().dynamicTypeSize(.accessibility2)
}

#Preview("TodoRow · accessibility2 dark") {
    TodoRowsPreview().dynamicTypeSize(.accessibility2).preferredColorScheme(.dark)
}

private struct DateChipPreview: View {
    var body: some View {
        HStack(spacing: 16) {
            DateChip(weekday: "Fri", day: "30", kind: .lunch)
            DateChip(weekday: "Sat", day: "7", kind: .activity)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AmbientBackground())
    }
}

#Preview("DateChip · light") {
    DateChipPreview().preferredColorScheme(.light)
}

#Preview("DateChip · dark") {
    DateChipPreview().preferredColorScheme(.dark)
}
