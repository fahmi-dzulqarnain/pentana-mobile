//
//  BillsView.swift
//  iosApp
//

@preconcurrency import Shared
import SwiftUI

struct BillsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: BillsStore?
    @State private var state: BillsUiState = BillsUiStateLoading.shared
    @State private var showSubmit = false

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeBillsStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                _ = await states
            }
            .sheet(isPresented: $showSubmit, onDismiss: { store?.resetSubmit() }) {
                if let store {
                    SubmitProofView(store: store)
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
                EmptyStateView(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg,
                               title: "Couldn't load", message: error.message,
                               actionTitle: "Try again", action: { store?.load() })
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    summaryCard(content.summary)
                        .padding(.top, 4)

                    Button { showSubmit = true } label: {
                        Label("Submit payment proof", systemImage: "camera.fill")
                    }
                    .buttonStyle(PentProminentButtonStyle())
                    .padding(.top, 14)

                    if content.bills.isEmpty {
                        EmptyStateView(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg,
                                       title: "No bills yet", message: "When dues are issued they'll appear here.")
                    } else {
                        SectionLabel(text: "Bill history")
                        InsetGroup {
                            ForEach(Array(content.bills.enumerated()), id: \.element.id) { index, bill in
                                BillRow(bill: bill)
                                if index < content.bills.count - 1 { PentHairline(leadingInset: 64) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            .refreshable { try? await store?.refresh() }
        }
    }

    private func summaryCard(_ summary: BillsSummaryDto) -> some View {
        ZStack(alignment: .topTrailing) {
            Image("PentanaMark")
                .resizable().scaledToFit()
                .frame(width: 120, height: 120)
                .rotationEffect(.degrees(18))
                .opacity(0.14)
                .offset(x: 28, y: -28)

            VStack(alignment: .leading, spacing: 0) {
                Text("TOTAL OUTSTANDING")
                    .font(.system(size: 12.5, weight: .semibold)).tracking(0.5)
                    .foregroundStyle(.white.opacity(0.7))
                Text("MYR \(summary.totalOutstanding)")
                    .font(.pentMoney(34, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.top, 4)
                HStack(spacing: 28) {
                    statBlock("Available credit", "MYR \(summary.availableCredit)")
                    statBlock("Unpaid bills", "\(Int(summary.unpaidCount))")
                }
                .padding(.top, 16)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Pent.indigo, Pent.indigo600], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: Pent.indigo.opacity(0.32), radius: 15, y: 12)
    }

    private func statBlock(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.pentMoney(21, weight: .bold)).foregroundStyle(.white)
            Text(label).font(.pentFoot).fontWeight(.medium).foregroundStyle(.white.opacity(0.7))
        }
    }
}

private struct BillRow: View {
    let bill: BillDto

    var body: some View {
        let sharedStatus = billStatus(bill: bill)
        HStack(alignment: .top, spacing: 12) {
            DomainIcon(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg, size: 36, corner: 10, iconSize: 18)
            VStack(alignment: .leading, spacing: 5) {
                Text(monthLabel(bill.month)).font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label)
                Text("Due MYR \(bill.amountDue) · Paid MYR \(bill.amountPaid)")
                    .font(.pentFoot).foregroundStyle(Pent.label2).monospacedDigit()
                    .lineSpacing(3)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 6) {
                Text("MYR \(bill.outstanding)")
                    .font(.pentMoney(16, weight: .bold))
                    .foregroundStyle(sharedStatus == .paid ? Pent.label3 : Pent.label)
                StatusPill(pillKind(for: sharedStatus))
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 15)
    }

    private func pillKind(for sharedStatus: BillStatus) -> PillKind {
        switch sharedStatus {
        case .paid: return .paid
        case .partial: return .partial
        case .overdue: return .overdue
        case .unpaid: return .unpaid
        }
    }

    /// "2026-06" -> "June 2026"
    private func monthLabel(_ month: String) -> String {
        let parser = DateFormatter(); parser.dateFormat = "yyyy-MM"; parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: month) else { return month }
        let formatter = DateFormatter(); formatter.dateFormat = "MMMM yyyy"
        return formatter.string(from: date)
    }
}
