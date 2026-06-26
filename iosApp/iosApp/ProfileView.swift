//
//  ProfileView.swift
//  iosApp
//
//  Account sheet from the top-left avatar. Member details + Sign out.
//

import Shared
import SwiftUI
import UIKit

struct ProfileView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var passkeys: [PasskeyDto] = []
    @State private var addingPasskey = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    // Header
                    VStack(spacing: 0) {
                        AvatarInitials(initials: pentInitials(session.user?.name), size: 84)
                            .shadow(color: Pent.orange.opacity(0.32), radius: 13, y: 10)
                        Text(session.user?.name ?? "Member")
                            .font(.pentTitle2).foregroundStyle(Pent.label).padding(.top, 12)
                        Text(session.user?.email ?? "")
                            .font(.pentCallout).foregroundStyle(Pent.label2).padding(.top, 2)
                    }
                    .padding(.top, 4).padding(.bottom, 18)

                    SectionLabel(text: "Membership")
                    InsetGroup {
                        infoRow("Category", session.user?.memberCategory ?? "—")
                        PentHairline()
                        infoRow("Birthday", birthday)
                        PentHairline()
                        infoRow("Credit balance", "MYR \(creditString)", valueColor: Pent.ok, mono: true)
                    }

                    SectionLabel(text: "Passkeys")
                    InsetGroup {
                        ForEach(passkeys, id: \.id) { pk in
                            HStack(spacing: 11) {
                                Image(systemName: "key.horizontal.fill").font(.system(size: 16)).foregroundStyle(Pent.proof)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(pk.name ?? "Passkey").font(.pentBody).foregroundStyle(Pent.label)
                                    Text(pk.lastUsedAt.flatMap(PentDates.relative).map { "Last used \($0)" } ?? "Never used")
                                        .font(.pentFoot).foregroundStyle(Pent.label2)
                                }
                                Spacer()
                                Button(role: .destructive) { Task { await removePasskey(pk.id) } } label: {
                                    Image(systemName: "trash").font(.system(size: 15)).foregroundStyle(Pent.bad)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal, 16).padding(.vertical, 13)
                            PentHairline()
                        }
                        Button { Task { await addPasskey() } } label: {
                            HStack(spacing: 11) {
                                if addingPasskey {
                                    ProgressView().controlSize(.small)
                                } else {
                                    Image(systemName: "plus.circle.fill").font(.system(size: 18)).foregroundStyle(Pent.accent)
                                }
                                Text("Set up a passkey").font(.pentBody).fontWeight(.medium).foregroundStyle(Pent.accent)
                                Spacer()
                            }
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(addingPasskey)
                    }

                    InsetGroup {
                        Button(role: .destructive) {
                            Task { await session.logout() }
                        } label: {
                            HStack(spacing: 11) {
                                Image(systemName: "rectangle.portrait.and.arrow.right").font(.system(size: 18)).foregroundStyle(Pent.bad)
                                Text("Sign out").font(.pentBody).fontWeight(.medium).foregroundStyle(Pent.bad)
                                Spacer()
                            }
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 16)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.tint(Pent.accent)
                }
            }
            .task { await loadPasskeys() }
        }
    }

    private func loadPasskeys() async {
        do { passkeys = try await session.passkey.list() } catch {}
    }
    private func addPasskey() async {
        addingPasskey = true
        if await session.registerPasskey(name: UIDevice.current.name) { await loadPasskeys() }
        addingPasskey = false
    }
    private func removePasskey(_ id: Int64) async {
        do { try await session.passkey.delete(id: id); await loadPasskeys() } catch {}
    }

    private func infoRow(_ label: String, _ value: String, valueColor: Color = Pent.label2, mono: Bool = false) -> some View {
        HStack {
            Text(label).font(.pentBody).foregroundStyle(Pent.label)
            Spacer()
            Text(value)
                .font(mono ? .pentMoney(16, weight: .semibold) : .pentBody)
                .foregroundStyle(valueColor)
        }
        .padding(.horizontal, 16).padding(.vertical, 13)
    }

    private var birthday: String {
        guard let b = session.user?.birthday, !b.isEmpty else { return "—" }
        let p = DateFormatter(); p.dateFormat = "yyyy-MM-dd"; p.locale = Locale(identifier: "en_US_POSIX")
        guard let d = p.date(from: b) else { return b }
        let o = DateFormatter(); o.dateFormat = "d MMMM"
        return o.string(from: d)
    }
    private var creditString: String {
        String(format: "%.2f", session.user?.credit ?? 0)
    }
}
