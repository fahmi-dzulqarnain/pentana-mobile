//
//  NewSupportTicketView.swift
//  iosApp
//
//  New question form: type, subject, message and an optional screenshot. Same pattern as
//  SubmitProofView (uppercase labels, dashed photo tile, button pinned to the bottom).
//

import PhotosUI
@preconcurrency import Shared
import SwiftUI

struct NewSupportTicketView: View {
    @Environment(\.dismiss) private var dismiss
    let store: SupportListStore
    /// Called with the new ticket's id so the list can open its conversation.
    var onSent: (Int64) -> Void = { _ in }

    @State private var type = "question"
    @State private var subject = ""
    @State private var message = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    @State private var preview: UIImage?
    @State private var photoError: String?
    @State private var createState: SupportCreateState = SupportCreateStateIdle.shared

    private var sending: Bool { createState is SupportCreateStateSending }
    private var errorMessage: String? { (createState as? SupportCreateStateError)?.message }
    private var ready: Bool {
        !subject.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && subject.count <= 150 && message.count <= 5000
            && !sending && !(createState is SupportCreateStateSent)
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

                    VStack(alignment: .leading, spacing: 6) {
                        Text("TYPE").font(.system(size: 12.5, weight: .semibold)).tracking(0.4)
                            .foregroundStyle(Pent.label2).padding(.horizontal, 4)
                        Picker("Type", selection: $type) {
                            Text(supportTypeLabel(type: "question")).tag("question")
                            Text(supportTypeLabel(type: "bug")).tag("bug")
                            Text(supportTypeLabel(type: "suggestion")).tag("suggestion")
                        }
                        .pickerStyle(.segmented)
                    }

                    PentField(label: "Subject", placeholder: "e.g. Paid twice for October?", text: $subject,
                              required: true, error: subject.count > 150 ? "Keep it under 150 characters." : nil)
                    PentField(label: "Message", placeholder: "Tell the committee what's going on…", text: $message,
                              multiline: true, required: true, error: message.count > 5000 ? "Keep it under 5000 characters." : nil)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("SCREENSHOT (OPTIONAL)").font(.system(size: 12.5, weight: .semibold)).tracking(0.4)
                            .foregroundStyle(Pent.label2).padding(.horizontal, 4)
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            ScreenshotTile(preview: preview)
                        }
                        .buttonStyle(.plain)
                        if let photoError {
                            Text(photoError).font(.pentFoot).fontWeight(.semibold).foregroundStyle(Pent.bad).padding(.horizontal, 4)
                        }
                    }
                }
                .padding(18)
            }
            .background(Pent.bgBase)
            .navigationTitle("New question")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(Pent.accent).disabled(sending)
                }
            }
            // No closing mid-send: a reopened form could send the same question twice.
            .interactiveDismissDisabled(sending)
            .safeAreaInset(edge: .bottom) {
                Button(action: send) {
                    if sending { ProgressView().tint(Pent.onBrand) } else { Text("Send to committee") }
                }
                .buttonStyle(PentProminentButtonStyle(enabled: ready))
                .disabled(!ready)
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 12)
                .background(.bar)
            }
            .onChange(of: photoItem) { _, item in
                Task {
                    // Same preparation as payment proofs: HEIC → JPEG under 5 MB, metadata stripped.
                    let raw = try? await item?.loadTransferable(type: Data.self)
                    let prepared = raw.flatMap(ProofImage.prepare)
                    await MainActor.run {
                        imageData = prepared?.jpeg
                        preview = prepared?.preview
                        photoError = (raw != nil && prepared == nil) ? "That image couldn't be read. Please choose another." : nil
                    }
                }
            }
        }
        .task {
            for await value in store.create {
                await MainActor.run { createState = value }
                if let sent = value as? SupportCreateStateSent {
                    await MainActor.run {
                        store.resetCreate()
                        dismiss()
                        onSent(sent.ticketId)
                    }
                }
            }
        }
    }

    private func send() {
        store.create(type: type, subject: subject, body: message,
                     screenshot: imageData.map { byteArrayFrom(data: $0) },
                     fileName: imageData == nil ? nil : "screenshot.jpg")
    }
}

private struct ScreenshotTile: View {
    let preview: UIImage?
    var body: some View {
        if let preview {
            HStack(spacing: 13) {
                Image(uiImage: preview)
                    .resizable().scaledToFill()
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityLabel("Selected screenshot")
                VStack(alignment: .leading, spacing: 1) {
                    Label("Screenshot added", systemImage: "checkmark.circle.fill")
                        .font(.pentBody).fontWeight(.semibold).foregroundStyle(Pent.label)
                    Text("Tap to choose a different one").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Spacer()
            }
            .padding(12)
            .background(Pent.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
        } else {
            HStack(spacing: 12) {
                DomainIcon(symbol: "photo.fill", tint: Pent.proof, bg: Pent.proofBg, size: 44, corner: 12, iconSize: 20)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Add screenshot").font(.pentCallout).fontWeight(.semibold).foregroundStyle(Pent.label)
                    Text("Choose from library").font(.pentFoot).foregroundStyle(Pent.label2)
                }
                Spacer()
            }
            .padding(12)
            .background(Pent.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [5]))
                    .foregroundStyle(Pent.separatorOpaque)
            )
        }
    }
}
