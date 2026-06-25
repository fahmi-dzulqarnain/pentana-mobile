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
        List {
            if let summary {
                Section("Summary") {
                    LabeledContent("Outstanding", value: "MYR \(summary.totalOutstanding)")
                    LabeledContent("Available credit", value: "MYR \(summary.availableCredit)")
                }
            }

            Section("Bills") {
                if bills.isEmpty && !isLoading {
                    Text("No bills yet.").foregroundStyle(.secondary)
                }
                ForEach(bills, id: \.id) { bill in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(bill.month).font(.headline)
                            Spacer()
                            Text(bill.status.capitalized)
                                .font(.caption.bold())
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(color(for: bill.status).opacity(0.15))
                                .foregroundStyle(color(for: bill.status))
                                .clipShape(Capsule())
                        }
                        Text("Due MYR \(bill.amountDue)  ·  Paid MYR \(bill.amountPaid)  ·  Outstanding MYR \(bill.outstanding)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .overlay { if isLoading && bills.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { showSubmit = true } label: { Label("Submit proof", systemImage: "plus.circle.fill") }
            }
        }
        .sheet(isPresented: $showSubmit) {
            SubmitProofView { Task { await load() } }
                .environmentObject(session)
        }
    }

    private func color(for status: String) -> Color {
        switch status {
        case "paid": return .green
        case "partial": return .orange
        default: return .red
        }
    }

    private func load() async {
        isLoading = true
        do {
            summary = try await session.bills.summary()
            bills = try await session.bills.bills()
        } catch {
            // Keep whatever we had; a banner could be added later.
        }
        isLoading = false
    }
}
