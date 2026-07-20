//
//  RegisterActivityView.swift
//  iosApp
//
//  Dynamic registration form: renders the activity's questions
//  (text / textarea / select / checkbox) and submits the answers.
//

@preconcurrency import Shared
import SwiftUI

struct RegisterActivityView: View {
    @Environment(\.dismiss) private var dismiss
    let store: ActivitiesStore
    let activity: ActivityDto

    @State private var answers: [String: String] = [:]
    @State private var regState: RegState = RegStateIdle.shared

    private var isSubmitting: Bool { regState is RegStateSubmitting }
    private var errorMessage: String? { (regState as? RegStateError)?.message }
    private var canRegister: Bool {
        requiredAnswered(questions: activity.questions, answers: answers) && !isSubmitting
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    banner

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

                    ForEach(activity.questions, id: \.key) { question in
                        questionField(question)
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
                .buttonStyle(PentProminentButtonStyle(enabled: canRegister))
                .disabled(!canRegister)
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 12)
                .background(.bar)
            }
        }
        .task {
            for await value in store.reg {
                await MainActor.run { regState = value }
                if value is RegStateSuccess {
                    await MainActor.run {
                        store.resetReg()
                        dismiss()
                    }
                }
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
    private func questionField(_ question: QuestionDto) -> some View {
        switch question.type {
        case "checkbox":
            Button {
                answers[question.key] = checkboxValue(checked: answers[question.key] != "true")
            } label: {
                HStack(spacing: 11) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(answers[question.key] == "true" ? Pent.accentSolid : Color.clear)
                            .frame(width: 24, height: 24)
                            .overlay(RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .strokeBorder(answers[question.key] == "true" ? Color.clear : Pent.label4, lineWidth: 1.5))
                        if answers[question.key] == "true" {
                            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Pent.onBrand)
                        }
                    }
                    Text(label(question)).font(.pentBody).foregroundStyle(Pent.label)
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
        case "select":
            selectField(question)
        case "textarea":
            PentField(label: label(question), placeholder: "Your answer", text: binding(question.key), multiline: true, required: question.required)
        default:
            PentField(label: label(question), placeholder: "Your answer", text: binding(question.key), required: question.required)
        }
    }

    private func selectField(_ question: QuestionDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            (Text(label(question).uppercased()) + (question.required ? Text(" *").foregroundColor(Pent.bad) : Text("")))
                .font(.system(size: 12.5, weight: .semibold)).tracking(0.4)
                .foregroundStyle(Pent.label2).padding(.horizontal, 4)
            Menu {
                ForEach(question.options ?? [], id: \.self) { opt in
                    Button(opt) { answers[question.key] = opt }
                }
            } label: {
                HStack {
                    Text(answers[question.key]?.isEmpty == false ? answers[question.key]! : "Select an option")
                        .font(.pentBody)
                        .foregroundStyle(answers[question.key]?.isEmpty == false ? Pent.label : Pent.label4)
                    Spacer()
                    Image(systemName: "chevron.down").font(.system(size: 14, weight: .semibold)).foregroundStyle(Pent.label3)
                }
                .padding(.horizontal, 14).frame(height: 48)
                .background(Pent.surface, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).strokeBorder(Pent.separator, lineWidth: 0.5))
            }
        }
    }

    private func label(_ question: QuestionDto) -> String { question.label }
    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { answers[key] ?? "" }, set: { answers[key] = $0 })
    }

    private func submit() {
        store.register(activityId: activity.id, answers: answers)
    }
}
