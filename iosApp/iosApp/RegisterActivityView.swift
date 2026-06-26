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
            Form {
                if let blurb = description, !blurb.isEmpty {
                    Section { Text(blurb).font(.subheadline) }
                }

                ForEach(activity.questions, id: \.key) { question in
                    if question.type == "checkbox" {
                        Section {
                            Toggle(isOn: boolBinding(question.key)) {
                                Text(label(question))
                            }
                        }
                    } else {
                        Section(header: Text(label(question))) {
                            field(question)
                        }
                    }
                }

                if let error {
                    Text(error).foregroundStyle(.red)
                }
            }
            .navigationTitle("Register")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") { Task { await submit() } }
                        .disabled(isSubmitting || !requiredAnswered)
                }
            }
            .overlay { if isSubmitting { ProgressView() } }
        }
    }

    // MARK: - Fields

    @ViewBuilder
    private func field(_ question: QuestionDto) -> some View {
        switch question.type {
        case "textarea":
            TextField("Your answer", text: binding(question.key), axis: .vertical)
                .lineLimit(3 ... 6)
        case "select":
            Picker("Select", selection: binding(question.key)) {
                Text("—").tag("")
                ForEach(question.options ?? [], id: \.self) { option in
                    Text(option).tag(option)
                }
            }
            .pickerStyle(.menu)
        default: // text
            TextField("Your answer", text: binding(question.key))
        }
    }

    private func label(_ question: QuestionDto) -> String {
        question.label + (question.required ? " *" : "")
    }

    // MARK: - Bindings

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { answers[key] ?? "" }, set: { answers[key] = $0 })
    }

    private func boolBinding(_ key: String) -> Binding<Bool> {
        // Send "1"/"0" so the API's `accepted` rule passes for ticked required boxes.
        Binding(get: { answers[key] == "1" }, set: { answers[key] = $0 ? "1" : "0" })
    }

    private var requiredAnswered: Bool {
        activity.questions.allSatisfy { question in
            guard question.required else { return true }
            if question.type == "checkbox" { return answers[question.key] == "1" }
            return !(answers[question.key] ?? "").trimmingCharacters(in: .whitespaces).isEmpty
        }
    }

    private var description: String? {
        guard let html = activity.description_, !html.isEmpty else { return nil }
        return html
            .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Submit

    private func submit() async {
        isSubmitting = true
        error = nil
        // Drop blank optional answers; keep explicit "0" for answered checkboxes.
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
