//
//  LoginView.swift
//  iosApp
//
//  Bespoke, glass-forward sign-in — the only unauthenticated screen.
//

import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false

    private var canSubmit: Bool { !email.isEmpty && !password.isEmpty && !isSubmitting }

    var body: some View {
        ZStack {
            AmbientBackground()

            ScrollView {
                VStack(spacing: 0) {
                    // Brand lockup
                    VStack(spacing: 0) {
                        Image("PentanaMark")
                            .resizable().scaledToFit()
                            .frame(width: 74, height: 74)
                            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .shadow(color: Pent.orange.opacity(0.30), radius: 15, y: 10)
                        Text("PENTANA")
                            .font(.system(size: 30, weight: .heavy))
                            .tracking(4.2)
                            .foregroundStyle(Pent.label)
                            .padding(.top, 18)
                        Text("Member sign in")
                            .font(.pentCallout).foregroundStyle(Pent.label2)
                            .padding(.top, 4)
                    }
                    .padding(.bottom, 30)

                    // Glass form card
                    VStack(spacing: 12) {
                        PentField(label: "Email", systemImage: "envelope.fill", placeholder: "you@org.my",
                                  text: $email, error: session.errorMessage != nil ? "" : nil,
                                  keyboard: .emailAddress, autocap: .never, disableAutocorrect: true)
                        PentField(label: "Password", systemImage: "lock.fill", placeholder: "Your password",
                                  text: $password, secure: true,
                                  error: session.errorMessage)

                        Button(action: submit) {
                            if isSubmitting {
                                ProgressView().tint(Pent.onBrand)
                            } else {
                                HStack(spacing: 7) {
                                    Text("Sign in")
                                    if canSubmit { Image(systemName: "arrow.right").font(.system(size: 16, weight: .semibold)) }
                                }
                            }
                        }
                        .buttonStyle(PentProminentButtonStyle(enabled: canSubmit))
                        .disabled(!canSubmit)
                        .padding(.top, 4)
                    }
                    .padding(18)
                    .pentGlass(22)

                    // Claim hint
                    VStack(spacing: 2) {
                        Text("Need access?").font(.pentFoot).foregroundStyle(Pent.label2)
                        Text("Claim your account on the web")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(Pent.accent)
                    }
                    .padding(.top, 22)
                }
                .padding(.horizontal, 28)
                .frame(maxWidth: .infinity)
                .frame(minHeight: UIScreen.main.bounds.height - 120)
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private func submit() {
        Task {
            isSubmitting = true
            await session.login(email: email, password: password)
            isSubmitting = false
        }
    }
}
