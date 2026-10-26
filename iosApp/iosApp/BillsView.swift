//
//  BillsView.swift
//  iosApp
//

@preconcurrency import Shared
import QuickLook
import SwiftUI

struct BillsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: BillsStore?
    @State private var state: BillsUiState = BillsUiStateLoading.shared
    @State private var showSubmit = false
    @State private var downloadingReceiptId: Int64?
    @State private var receiptURL: URL?
    @State private var receiptError: String?

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeBillsStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                async let receipts: Void = { for await value in activeStore.receipt { await MainActor.run { handleReceipt(value) } } }()
                _ = await (states, receipts)
            }
            // A download that finishes after the member has left Bills must not pop a viewer later.
            .onDisappear { store?.consumeReceipt() }
            .quickLookPreview($receiptURL)
            .alert(
                "Receipt",
                isPresented: Binding(get: { receiptError != nil }, set: { if !$0 { receiptError = nil } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(receiptError ?? "")
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

                    // The member's own submissions, so a pending one is visible and a rejection's reason
                    // stays readable after the push is gone. The Home "Payment proofs" card opens here.
                    if !content.proofs.isEmpty {
                        SectionLabel(text: "Payment proofs")
                        InsetGroup {
                            ForEach(Array(content.proofs.enumerated()), id: \.element.id) { index, proof in
                                ProofRow(proof: proof,
                                         downloadingReceiptId: downloadingReceiptId,
                                         onResubmit: { showSubmit = true },
                                         onReceipt: { store?.openReceipt(proofId: proof.id) })
                                if index < content.proofs.count - 1 { PentHairline(leadingInset: 64) }
                            }
                        }
                    }
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

    /// Each Ready/Error is shown once, then handed back to the store so a re-collect can't replay it.
    private func handleReceipt(_ receiptState: ReceiptState) {
        switch onEnum(of: receiptState) {
        case .idle:
            downloadingReceiptId = nil
        case .downloading(let downloading):
            downloadingReceiptId = downloading.proofId
        case .ready(let ready):
            downloadingReceiptId = nil
            do {
                receiptURL = try writeReceipt(ready.pdf)
            } catch {
                receiptError = "Couldn't download the receipt. Please try again."
            }
            store?.consumeReceipt()
        case .error(let error):
            downloadingReceiptId = nil
            receiptError = error.message
            store?.consumeReceipt()
        }
    }

    /// QuickLook needs a file; receipts go in tmp/receipts, overwritten by name.
    private func writeReceipt(_ pdf: ReceiptPdf) throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("receipts", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let fileURL = folder.appendingPathComponent(pdf.fileName)
        try (DataConversionKt.dataFrom(bytes: pdf.bytes) as Data).write(to: fileURL, options: .atomic)
        return fileURL
    }

    private func summaryCard(_ summary: BillsSummaryDto) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("TOTAL OUTSTANDING")
                .font(.system(size: 12.5, weight: .semibold)).tracking(0.5)
                .foregroundStyle(.white.opacity(0.7))
            Text("MYR \(summary.totalOutstanding)")
                .font(.pentMoney(34, weight: .bold))
                .foregroundStyle(.white)
                .padding(.top, 4)
            Rectangle()
                .fill(.white.opacity(0.18))
                .frame(height: 1)
                .padding(.top, 16)
            HStack(spacing: 28) {
                statBlock("Available credit", "MYR \(summary.availableCredit)")
                statBlock("Unpaid bills", "\(Int(summary.unpaidCount))")
            }
            .padding(.top, 14)
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

private struct ProofRow: View {
    let proof: PaymentProofDto
    let downloadingReceiptId: Int64?
    let onResubmit: () -> Void
    let onReceipt: () -> Void

    var body: some View {
        let status = proofStatus(proof: proof)
        HStack(alignment: .top, spacing: 12) {
            DomainIcon(symbol: "doc.text.fill", tint: Pent.proof, bg: Pent.proofBg, size: 36, corner: 10, iconSize: 18)
            VStack(alignment: .leading, spacing: 5) {
                Text("MYR \(proof.amountClaimed)").font(.pentMoney(16, weight: .bold)).foregroundStyle(Pent.label)
                if let submitted = proof.submittedAt.flatMap(PentDates.dateTime) {
                    Text("Submitted \(submitted)").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                if status == .verified, let verified = proof.amountVerified, verified != proof.amountClaimed {
                    Text("Verified as MYR \(verified)").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                if status == .rejected {
                    if let reason = proof.adminNote, !reason.isEmpty {
                        Text("Reason: \(reason)").font(.pentFoot).fontWeight(.medium).foregroundStyle(Pent.bad)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Button("Submit a new proof", action: onResubmit)
                        .font(.pentFoot).fontWeight(.semibold).tint(Pent.accent)
                        .padding(.top, 2)
                }
                if status == .verified && proof.hasReceipt {
                    receiptButton
                }
            }
            Spacer(minLength: 8)
            StatusPill(pillKind(for: status))
        }
        .padding(.horizontal, 16).padding(.vertical, 15)
        .accessibilityElement(children: .contain)
    }

    /// The number sits on its own line: beside the button it wrapped mid-number on a narrow row.
    private var receiptButton: some View {
        VStack(alignment: .leading, spacing: 2) {
            if downloadingReceiptId == proof.id {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Opening receipt…").font(.pentFoot).foregroundStyle(Pent.label2)
                }
            } else {
                Button("View receipt", action: onReceipt)
                    .font(.pentFoot).fontWeight(.semibold).tint(Pent.accent)
                    .disabled(downloadingReceiptId != nil)
            }
            if let receiptNumber = proof.receiptNumber {
                Text(receiptNumber).font(.pentFoot).foregroundStyle(Pent.label2).monospacedDigit().lineLimit(1)
            }
        }
        .padding(.top, 2)
    }

    private func pillKind(for status: ProofStatus) -> PillKind {
        switch status {
        case .verified: return .proofVerified
        case .rejected: return .proofRejected
        case .pending: return .proofPending
        }
    }
}
