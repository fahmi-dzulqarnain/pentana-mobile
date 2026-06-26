//
//  BillsView.swift
//  iosApp
//

import Shared
import SwiftUI

struct BillsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var summary: BillsSummaryDto?
    @State private var bills: [BillDto] = []
    @State private var isLoading = true
    @State private var showSubmit = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                summaryCard
                    .padding(.top, 4)

                Button { showSubmit = true } label: {
                    Label("Submit payment proof", systemImage: "camera.fill")
                }
                .buttonStyle(PentProminentButtonStyle())
                .padding(.top, 14)

                if bills.isEmpty && !isLoading {
                    EmptyStateView(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg,
                                   title: "No bills yet", message: "When dues are issued they'll appear here.")
                } else {
                    SectionLabel(text: "Bill history")
                    InsetGroup {
                        ForEach(Array(bills.enumerated()), id: \.element.id) { index, bill in
                            BillRow(bill: bill)
                            if index < bills.count - 1 { PentHairline(leadingInset: 64) }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
        .overlay { if isLoading && bills.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .sheet(isPresented: $showSubmit) {
            SubmitProofView { Task { await load() } }
                .environmentObject(session)
        }
    }

    private var summaryCard: some View {
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
                Text("MYR \(summary?.totalOutstanding ?? "0.00")")
                    .font(.pentMoney(34, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.top, 4)
                HStack(spacing: 28) {
                    statBlock("Available credit", "MYR \(summary?.availableCredit ?? "0.00")")
                    statBlock("Unpaid bills", "\(summary.map { Int($0.unpaidCount) } ?? 0)")
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

    private func load() async {
        isLoading = true
        do {
            summary = try await session.bills.summary()
            bills = try await session.bills.bills()
        } catch {}
        isLoading = false
    }
}

private struct BillRow: View {
    let bill: BillDto

    var body: some View {
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
                    .foregroundStyle(bill.status == "paid" ? Pent.label3 : Pent.label)
                StatusPill(pill(bill.status))
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 15)
    }

    private func pill(_ status: String) -> PillKind {
        switch status {
        case "paid": return .paid
        case "partial": return .partial
        case "overdue": return .overdue
        default: return .unpaid
        }
    }

    /// "2026-06" -> "June 2026"
    private func monthLabel(_ m: String) -> String {
        let p = DateFormatter(); p.dateFormat = "yyyy-MM"; p.locale = Locale(identifier: "en_US_POSIX")
        guard let d = p.date(from: m) else { return m }
        let o = DateFormatter(); o.dateFormat = "MMMM yyyy"
        return o.string(from: d)
    }
}
