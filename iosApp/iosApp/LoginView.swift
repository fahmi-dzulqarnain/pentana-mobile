//
//  LoginView.swift
//  iosApp
//

import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text("PENTANA").font(.largeTitle.bold())
            Text("Member sign in").foregroundStyle(.secondary)

            TextField("Email", text: $email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)

            SecureField("Password", text: $password)
                .textContentType(.password)
                .textFieldStyle(.roundedBorder)

            if let error = session.errorMessage {
                Text(error).foregroundStyle(.red).font(.footnote)
            }

            Button {
                Task {
                    isSubmitting = true
                    await session.login(email: email, password: password)
                    isSubmitting = false
                }
            } label: {
                if isSubmitting {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Sign in").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(email.isEmpty || password.isEmpty || isSubmitting)

            Spacer()
        }
        .padding(24)
    }
}
