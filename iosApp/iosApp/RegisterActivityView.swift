//
//  RegisterActivityView.swift
//  iosApp
//
//  Dynamic registration form: renders the activity's questions
//  (text / textarea / select / checkbox) and submits the answers.
//

import Shared
import SwiftUI

struct RegisterActivityView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss

    let activity: ActivityDto
    var onRegistered: (ActivityDto) -> Void

    @State private var answers: [String: String] = [:]
    @State private var isSubmitting = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    banner

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

                    ForEach(activity.questions, id: \.key) { q in
                        questionField(q)
                    }
                }
                .padding(18)
            }
            .navigationTitle("Register")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(Pent.accent)
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button(action: submit) {
                    if isSubmitting { ProgressView().tint(Pent.onBrand) } else { Text("Register") }
                }
                .buttonStyle(PentProminentButtonStyle(enabled: requiredAnswered && !isSubmitting))
                .disabled(!requiredAnswered || isSubmitting)
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 12)
                .background(.bar)
            }
        }
    }

    private var banner: some View {
        Text("\(activity.title)\(activity.startsAt.flatMap(PentDates.dateTime).map { " · " + $0 } ?? ""). Please answer a few questions so we can plan ahead.")
            .font(.pentFoot).fontWeight(.medium)
            .foregroundStyle(Pent.activ)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 13).padding(.vertical, 11)
            .background(Pent.activBg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private func questionField(_ q: QuestionDto) -> some View {
        switch q.type {
        case "checkbox":
            Button {
                answers[q.key] = (answers[q.key] == "1") ? "0" : "1"
            } label: {
                HStack(spacing: 11) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(answers[q.key] == "1" ? Pent.accentSolid : Color.clear)
                            .frame(width: 24, height: 24)
                            .overlay(RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .strokeBorder(answers[q.key] == "1" ? Color.clear : Pent.label4, lineWidth: 1.5))
                        if answers[q.key] == "1" {
                            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Pent.onBrand)
                        }
                    }
                    Text(label(q)).font(.pentBody).foregroundStyle(Pent.label)
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
        case "select":
            selectField(q)
        case "textarea":
            PentField(label: label(q), placeholder: "Your answer", text: binding(q.key), multiline: true, required: q.required)
        default:
            PentField(label: label(q), placeholder: "Your answer", text: binding(q.key), required: q.required)
        }
    }

    private func selectField(_ q: QuestionDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            (Text(label(q).uppercased()) + (q.required ? Text(" *").foregroundColor(Pent.bad) : Text("")))
                .font(.system(size: 12.5, weight: .semibold)).tracking(0.4)
                .foregroundStyle(Pent.label2).padding(.horizontal, 4)
            Menu {
                ForEach(q.options ?? [], id: \.self) { opt in
                    Button(opt) { answers[q.key] = opt }
                }
            } label: {
                HStack {
                    Text(answers[q.key]?.isEmpty == false ? answers[q.key]! : "Select an option")
                        .font(.pentBody)
                        .foregroundStyle(answers[q.key]?.isEmpty == false ? Pent.label : Pent.label4)
                    Spacer()
                    Image(systemName: "chevron.down").font(.system(size: 14, weight: .semibold)).foregroundStyle(Pent.label3)
                }
                .padding(.horizontal, 14).frame(height: 48)
                .background(Pent.surface, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
            }
        }
    }

    private func label(_ q: QuestionDto) -> String { q.label }
    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { answers[key] ?? "" }, set: { answers[key] = $0 })
    }
    private var requiredAnswered: Bool {
        activity.questions.allSatisfy { q in
            guard q.required else { return true }
            if q.type == "checkbox" { return answers[q.key] == "1" }
            return !(answers[q.key] ?? "").trimmingCharacters(in: .whitespaces).isEmpty
        }
    }

    private func submit() {
        Task {
            isSubmitting = true
            error = nil
            let payload = answers.filter { !$0.value.isEmpty }
            do {
                let updated = try await session.activities.register(activityId: activity.id, answers: payload)
                onRegistered(updated)
                dismiss()
            } catch {
                self.error = "Registration failed. Please check your answers and try again."
            }
            isSubmitting = false
        }
    }
}
