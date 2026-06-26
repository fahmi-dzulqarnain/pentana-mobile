//
//  ProfileView.swift
//  iosApp
//
//  Account screen reached from the top-left profile button. Shows the member's
//  details (from the cached session) and is the home for Sign out.
//

import Shared
import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if let user = session.user {
                    Section {
                        VStack(spacing: 8) {
                            Image(systemName: "person.crop.circle.fill")
                                .font(.system(size: 56))
                                .foregroundStyle(.tint)
                            Text(user.name).font(.title3.bold())
                            Text(user.email).font(.subheadline).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    }

                    Section("Membership") {
                        if let category = user.memberCategory, !category.isEmpty {
                            row("Category", category)
                        }
                        if let birthday = user.birthday, !birthday.isEmpty {
                            row("Birthday", formattedBirthday(birthday))
                        }
                        row("Credit", "MYR " + String(format: "%.2f", user.credit))
                    }
                }

                Section {
                    Button(role: .destructive) {
                        Task { await session.logout() }
                    } label: {
                        Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value).foregroundStyle(.secondary)
        }
    }

    private func formattedBirthday(_ ymd: String) -> String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: ymd) else { return ymd }
        let out = DateFormatter()
        out.dateFormat = "d MMMM"
        return out.string(from: date)
    }
}
