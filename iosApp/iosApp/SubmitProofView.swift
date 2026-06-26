//
//  SubmitProofView.swift
//  iosApp
//

import PhotosUI
import Shared
import SwiftUI

struct SubmitProofView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss

    var onSubmitted: () -> Void

    @State private var amount = ""
    @State private var note = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    @State private var isSubmitting = false
    @State private var error: String?

    private var ready: Bool { imageData != nil && Double(amount) != nil && !isSubmitting }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if let error {
                        HStack(spacing: 9) {
                            Image(systemName: "exclamationmark.circle.fill")
                            Text(error).font(.pentFoot).fontWeight(.semibold)
                        }
                        .foregroundStyle(Pent.bad)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 13).padding(.vertical, 11)
                        .background(Pent.badBg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }

                    PentField(label: "Amount (MYR)", systemImage: "banknote.fill", placeholder: "0.00",
                              text: $amount, required: true, keyboard: .decimalPad)
                    PentField(label: "Note (optional)", placeholder: "Add a note for the reviewer",
                              text: $note, multiline: true)

                    VStack(alignment: .leading, spacing: 6) {
                        (Text("RECEIPT PHOTO ") + Text("*").foregroundColor(Pent.bad))
                            .font(.system(size: 12.5, weight: .semibold)).tracking(0.4)
                            .foregroundStyle(Pent.label2).padding(.horizontal, 4)
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            PhotoTile(hasPhoto: imageData != nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(18)
            }
            .navigationTitle("Submit payment proof")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(Pent.accent)
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button(action: submit) {
                    if isSubmitting { ProgressView().tint(Pent.onBrand) } else { Text("Submit proof") }
                }
                .buttonStyle(PentProminentButtonStyle(enabled: ready))
                .disabled(!ready)
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 12)
                .background(.bar)
            }
            .onChange(of: photoItem) { _, item in
                Task { imageData = try? await item?.loadTransferable(type: Data.self) }
            }
        }
    }

    private func submit() {
        guard let data = imageData else { return }
        Task {
            isSubmitting = true
            error = nil
            do {
                _ = try await session.bills.submitPaymentProof(
                    imageBytes: data.toKotlinByteArray(), fileName: "proof.jpg",
                    amountClaimed: amount, memberNote: note.isEmpty ? nil : note
                )
                onSubmitted()
                dismiss()
            } catch {
                self.error = "Upload failed. Please try again."
            }
            isSubmitting = false
        }
    }
}

struct PhotoTile: View {
    let hasPhoto: Bool
    var body: some View {
        if hasPhoto {
            HStack(spacing: 13) {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(LinearGradient(colors: [Pent.proofBg, Pent.lunchBg], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 56, height: 56)
                    .overlay(Image(systemName: "doc.text.fill").font(.system(size: 22)).foregroundStyle(Pent.proof))
                VStack(alignment: .leading, spacing: 1) {
                    Label("Photo selected", systemImage: "checkmark.circle.fill")
                        .font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label)
                        .labelStyle(.titleAndIcon).tint(Pent.ok)
                    Text("receipt.jpg").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Spacer()
            }
            .padding(12)
            .background(Pent.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
        } else {
            VStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Pent.proofBg)
                    .frame(width: 44, height: 44)
                    .overlay(Image(systemName: "camera.fill").font(.system(size: 20)).foregroundStyle(Pent.proof))
                Text("Add receipt photo").font(.pentCallout).fontWeight(.semibold).foregroundStyle(Pent.label)
                Text("Take a photo or choose from library").font(.pentFoot).foregroundStyle(Pent.label2)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 22)
            .background(Pent.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [5]))
                    .foregroundStyle(Pent.separatorOpaque)
            )
        }
    }
}

private extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return array
    }
}
