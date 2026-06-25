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

    /// Called after a successful submit so the caller can refresh.
    var onSubmitted: () -> Void

    @State private var amount = ""
    @State private var note = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    @State private var isSubmitting = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Amount (MYR)") {
                    TextField("e.g. 50.00", text: $amount)
                        .keyboardType(.decimalPad)
                }
                Section("Note (optional)") {
                    TextField("Reference / remarks", text: $note, axis: .vertical)
                }
                Section("Receipt") {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label(imageData == nil ? "Choose photo" : "Photo selected",
                              systemImage: imageData == nil ? "photo" : "checkmark.circle.fill")
                    }
                }
                if let error {
                    Text(error).foregroundStyle(.red)
                }
            }
            .navigationTitle("Submit proof")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") { Task { await submit() } }
                        .disabled(imageData == nil || Double(amount) == nil || isSubmitting)
                }
            }
            .onChange(of: photoItem) { _, newItem in
                Task { imageData = try? await newItem?.loadTransferable(type: Data.self) }
            }
            .overlay { if isSubmitting { ProgressView() } }
        }
    }

    private func submit() async {
        guard let data = imageData else { return }
        isSubmitting = true
        error = nil
        do {
            _ = try await session.bills.submitPaymentProof(
                imageBytes: data.toKotlinByteArray(),
                fileName: "proof.jpg",
                amountClaimed: amount,
                memberNote: note.isEmpty ? nil : note
            )
            onSubmitted()
            dismiss()
        } catch {
            self.error = "Upload failed. Please try again."
        }
        isSubmitting = false
    }
}

private extension Data {
    /// Bridge Swift `Data` -> Kotlin `ByteArray` (per-byte; fine for receipt-sized images).
    func toKotlinByteArray() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return array
    }
}
