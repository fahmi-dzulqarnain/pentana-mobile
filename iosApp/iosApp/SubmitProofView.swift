//
//  SubmitProofView.swift
//  iosApp
//

import PhotosUI
@preconcurrency import Shared
import SwiftUI

struct SubmitProofView: View {
    @Environment(\.dismiss) private var dismiss
    let store: BillsStore

    @State private var amount = ""
    @State private var note = ""
    @State private var photoItem: PhotosPickerItem?
    /// The picked photo, already converted to an uploadable JPEG (see ProofImage).
    @State private var imageData: Data?
    @State private var preview: UIImage?
    @State private var photoError: String?
    @State private var submitState: SubmitState = SubmitStateIdle.shared

    private var isSubmitting: Bool { submitState is SubmitStateSubmitting }
    private var errorMessage: String? { (submitState as? SubmitStateError)?.message }
    private var ready: Bool {
        // Also disabled once Success lands - in the hop before resetSubmit()+dismiss() the store's
        // Submitting guard is already disarmed, so a live button could fire a duplicate upload.
        imageData != nil && canSubmitProof(amount: amount, hasPhoto: imageData != nil) && !isSubmitting && !(submitState is SubmitStateSuccess)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if let errorMessage {
                        HStack(spacing: 9) {
                            Image(systemName: "exclamationmark.circle.fill")
                            Text(errorMessage).font(.pentFoot).fontWeight(.semibold)
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
                            PhotoTile(preview: preview)
                        }
                        .buttonStyle(.plain)
                        if let photoError {
                            Text(photoError).font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.bad).padding(.horizontal, 4)
                        }
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
                Task {
                    let raw = try? await item?.loadTransferable(type: Data.self)
                    let prepared = raw.flatMap(ProofImage.prepare)
                    await MainActor.run {
                        imageData = prepared?.jpeg
                        preview = prepared?.preview
                        photoError = (raw != nil && prepared == nil) ? "That photo couldn't be read. Please choose another." : nil
                    }
                }
            }
        }
        .task {
            for await value in store.submit {
                await MainActor.run { submitState = value }
                if value is SubmitStateSuccess {
                    await MainActor.run {
                        store.resetSubmit()
                        dismiss()
                    }
                }
            }
        }
    }

    private func submit() {
        guard let data = imageData else { return }
        // byteArrayFrom is a shared iosMain helper: one memcpy instead of a bridged call per byte.
        store.submitProof(imageBytes: byteArrayFrom(data: data), fileName: "proof.jpg",
                          amount: amount, note: note)
    }
}

struct PhotoTile: View {
    let preview: UIImage?
    var body: some View {
        if let preview {
            HStack(spacing: 13) {
                // The member's actual photo, so they can check it's the right receipt.
                Image(uiImage: preview)
                    .resizable().scaledToFill()
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityLabel("Selected receipt photo")
                VStack(alignment: .leading, spacing: 1) {
                    Label("Photo selected", systemImage: "checkmark.circle.fill")
                        .font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label)
                        .labelStyle(.titleAndIcon).tint(Pent.ok)
                    Text("Tap to choose a different photo").font(.pentFoot).foregroundStyle(Pent.label2)
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

/// Turns whatever the photo picker returns into something the server accepts. iPhone camera
/// photos are HEIC, but proofs must be JPEG, PNG or WebP under 5 MB, so every pick is decoded,
/// scaled down to a readable size and re-encoded as JPEG.
enum ProofImage {
    static let maxDimension: CGFloat = 2000
    static let maxBytes = 4_500_000 // under the server's 5 MB limit

    static func prepare(_ data: Data) -> (jpeg: Data, preview: UIImage)? {
        guard let image = UIImage(data: data) else { return nil }
        let scaled = downscaled(image)
        for quality in [0.8, 0.65, 0.5] as [CGFloat] {
            if let jpeg = scaled.jpegData(compressionQuality: quality), jpeg.count <= maxBytes {
                return (jpeg, scaled)
            }
        }
        return nil
    }

    /// Always redrawn, even when small enough: that bakes in the photo's rotation and drops its
    /// metadata, including the GPS location, which has no business on a receipt upload.
    private static func downscaled(_ image: UIImage) -> UIImage {
        let longest = max(image.size.width, image.size.height)
        let factor = min(1, maxDimension / longest)
        let size = CGSize(width: (image.size.width * factor).rounded(), height: (image.size.height * factor).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }
}
