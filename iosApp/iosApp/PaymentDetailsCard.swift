//
//  PaymentDetailsCard.swift
//  iosApp
//

@preconcurrency import Shared
import SwiftUI
import UIKit

/// Where to transfer fees: bank, account number and the member's reference (each copyable), plus the
/// club's DuitNow QR. A banking app can't scan a QR on its own screen, so the QR opens full size with
/// Share, which offers Save Image for loading it into the bank app from Photos.
struct PaymentDetailsCard: View {
    let details: PaymentDetailsDto
    let store: BillsStore
    /// The proof sheet shows only the account rows; the Bills tab shows the QR too.
    var showsQr = true

    @State private var qr: UIImage?
    @State private var showQr = false
    @State private var copied: String?

    var body: some View {
        InsetGroup {
            row("Bank", details.bankName)
            PentHairline()
            row("Account number", details.accountNumber, copyable: true)
            PentHairline()
            row("Account name", details.accountName)
            PentHairline()
            row("Reference", details.reference, copyable: true)
            if showsQr, let qr {
                PentHairline()
                Button { showQr = true } label: {
                    HStack(spacing: 13) {
                        Image(uiImage: qr)
                            .resizable().scaledToFit()
                            .frame(width: 44, height: 62)
                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("DuitNow QR").font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label)
                            Text("Tap to view or save").font(.pentFoot).foregroundStyle(Pent.label2)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Pent.label3)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .task(id: details.qrUrl) {
            guard showsQr, qr == nil, let bytes = try? await store.paymentQr(url: details.qrUrl) else { return }
            let image = UIImage(data: DataConversionKt.dataFrom(bytes: bytes) as Data)
            await MainActor.run { qr = image }
        }
        .sheet(isPresented: $showQr) {
            if let qr { QrSheet(image: qr, accountName: details.accountName) }
        }
    }

    private func row(_ label: String, _ value: String, copyable: Bool = false) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.pentFoot).foregroundStyle(Pent.label2)
                Text(value).font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label)
                    .monospacedDigit().textSelection(.enabled)
            }
            Spacer(minLength: 8)
            if copyable {
                Button {
                    UIPasteboard.general.string = value
                    copied = label
                    Task {
                        try? await Task.sleep(for: .seconds(1.5))
                        await MainActor.run { if copied == label { copied = nil } }
                    }
                } label: {
                    Label(copied == label ? "Copied" : "Copy", systemImage: copied == label ? "checkmark" : "doc.on.doc")
                        .font(.pentFoot).fontWeight(.semibold)
                }
                .tint(Pent.accent)
                .accessibilityLabel(copied == label ? "Copied" : "Copy \(label.lowercased())")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
    }
}

private struct QrSheet: View {
    @Environment(\.dismiss) private var dismiss
    let image: UIImage
    let accountName: String

    var body: some View {
        NavigationStack {
            ScrollView {
                Image(uiImage: image)
                    .resizable().scaledToFit()
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityLabel("DuitNow QR for \(accountName)")
                    .padding(20)
            }
            .navigationTitle("DuitNow QR")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }.tint(Pent.accent)
                }
                ToolbarItem(placement: .primaryAction) {
                    let shareable = Image(uiImage: image)
                    ShareLink(item: shareable, preview: SharePreview("DuitNow QR – \(accountName)", image: shareable))
                        .tint(Pent.accent)
                }
            }
        }
    }
}
