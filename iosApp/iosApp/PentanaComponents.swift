//
//  PentanaComponents.swift
//  iosApp
//
//  Shared building blocks for the PENTANA design: domain icon tiles, status
//  pills, inset-grouped rows, the prominent CTA, avatar, and empty states.
//

import SwiftUI

// MARK: - Domain icon tile

struct DomainIcon: View {
    let symbol: String
    var tint: Color = Pent.dues
    var bg: Color = Pent.duesBg
    var size: CGFloat = 40
    var corner: CGFloat = 11
    var iconSize: CGFloat = 21

    var body: some View {
        RoundedRectangle(cornerRadius: corner, style: .continuous)
            .fill(bg)
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: symbol)
                    .font(.system(size: iconSize, weight: .semibold))
                    .foregroundStyle(tint)
            )
    }
}

// MARK: - Status pill

enum PillKind {
    case paid, partial, unpaid, overdue
    case registered, waitlisted, open, closed, voteNow, responded

    var label: String {
        switch self {
        case .paid: return "Paid"
        case .partial: return "Partial"
        case .unpaid: return "Unpaid"
        case .overdue: return "Overdue"
        case .registered: return "Registered"
        case .waitlisted: return "Waitlisted"
        case .open: return "Open"
        case .closed: return "Closed"
        case .voteNow: return "Vote now"
        case .responded: return "Responded"
        }
    }
    var color: Color {
        switch self {
        case .paid, .registered, .responded: return Pent.ok
        case .partial, .waitlisted, .voteNow: return Pent.warn
        case .unpaid, .closed: return Pent.neutral
        case .overdue: return Pent.bad
        case .open: return Pent.activ
        }
    }
    var bg: Color {
        switch self {
        case .paid, .registered, .responded: return Pent.okBg
        case .partial, .waitlisted, .voteNow: return Pent.warnBg
        case .unpaid, .closed: return Pent.neutralBg
        case .overdue: return Pent.badBg
        case .open: return Pent.activBg
        }
    }
    var dot: Bool {
        switch self {
        case .paid, .partial, .unpaid, .overdue, .voteNow: return true
        default: return false
        }
    }
    var icon: String? {
        switch self {
        case .registered, .responded: return "checkmark"
        case .waitlisted: return "clock.fill"
        default: return nil
        }
    }
}

struct StatusPill: View {
    var label: String
    var color: Color
    var bg: Color
    var dot: Bool = false
    var icon: String? = nil

    init(_ kind: PillKind) {
        label = kind.label; color = kind.color; bg = kind.bg; dot = kind.dot; icon = kind.icon
    }
    init(_ label: String, color: Color, bg: Color) {
        self.label = label; self.color = color; self.bg = bg
    }

    var body: some View {
        HStack(spacing: 4) {
            if dot { Circle().fill(color).frame(width: 6, height: 6) }
            if let icon { Image(systemName: icon).font(.system(size: 11, weight: .bold)) }
            Text(label).font(.system(size: 12.5, weight: .semibold))
        }
        .foregroundStyle(color)
        .padding(.vertical, 4)
        .padding(.horizontal, (dot || icon != nil) ? 8 : 10)
        .background(bg, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        .lineLimit(1)
        .fixedSize()
    }
}

// MARK: - Inset-grouped container

/// Opaque grouped container (cards/rows) with a hairline border.
struct InsetGroup<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 0) { content }
            .background(Pent.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(Pent.separator, lineWidth: 0.5)
            )
    }
}

/// Inset hairline between rows (leading inset matches a 64pt icon gutter, or 16pt).
struct PentHairline: View {
    var leadingInset: CGFloat = 16
    var body: some View {
        Rectangle()
            .fill(Pent.separator)
            .frame(height: 0.5)
            .padding(.leading, leadingInset)
    }
}

struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 12.5, weight: .semibold))
            .tracking(0.5)
            .foregroundStyle(Pent.label3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
            .padding(.top, 18)
            .padding(.bottom, 7)
    }
}

// MARK: - Prominent CTA

struct PentProminentButtonStyle: ButtonStyle {
    var enabled: Bool = true
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16.5, weight: .semibold))
            .foregroundStyle(Pent.onBrand)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(
                LinearGradient(colors: [Pent.accentSolid, Pent.orange600],
                               startPoint: .top, endPoint: .bottom),
                in: RoundedRectangle(cornerRadius: 15, style: .continuous)
            )
            .shadow(color: Pent.orange.opacity(0.36), radius: 9, y: 6)
            .opacity(enabled ? 1 : 0.45)
            .saturation(enabled ? 1 : 0.6)
            .scaleEffect(configuration.isPressed ? 0.99 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

// MARK: - Avatar

struct AvatarInitials: View {
    var initials: String
    var size: CGFloat = 30
    var body: some View {
        Circle()
            .fill(LinearGradient(colors: [Pent.orange, Pent.orange600],
                                 startPoint: .topLeading, endPoint: .bottomTrailing))
            .frame(width: size, height: size)
            .overlay(
                Text(initials)
                    .font(.system(size: size * 0.42, weight: .bold, design: .rounded))
                    .foregroundStyle(Pent.onBrand)
            )
    }
}

/// First initials of a name (max 2).
func pentInitials(_ name: String?) -> String {
    let parts = (name ?? "").split(separator: " ").prefix(2)
    let s = parts.compactMap { $0.first }.map(String.init).joined()
    return s.isEmpty ? "·" : s.uppercased()
}

// MARK: - Empty state

struct EmptyStateView: View {
    var symbol: String = "tray"
    var tint: Color = Pent.label3
    var bg: Color = Pent.surface2
    var title: String
    var message: String? = nil
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(bg)
                .frame(width: 72, height: 72)
                .overlay(Image(systemName: symbol).font(.system(size: 32, weight: .regular)).foregroundStyle(tint))
                .padding(.bottom, 8)
            Text(title).font(.pentTitle3).foregroundStyle(Pent.label)
            if let message {
                Text(message)
                    .font(.pentCallout).foregroundStyle(Pent.label2)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 240)
            }
            if let actionTitle, let action {
                Button(action: action) {
                    Label(actionTitle, systemImage: "arrow.clockwise")
                        .font(.pentBodyMedium)
                        .foregroundStyle(Pent.accent)
                        .padding(.vertical, 10).padding(.horizontal, 16)
                        .background(Pent.surface2, in: Capsule())
                }
                .padding(.top, 12)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 40)
        .padding(.vertical, 48)
    }
}

// MARK: - Text field

struct PentField: View {
    var label: String? = nil
    var systemImage: String? = nil
    var placeholder: String
    @Binding var text: String
    var secure: Bool = false
    var multiline: Bool = false
    var required: Bool = false
    var error: String? = nil
    var keyboard: UIKeyboardType = .default
    var autocap: TextInputAutocapitalization = .sentences
    var disableAutocorrect: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let label {
                (Text(label.uppercased()) + (required ? Text(" *").foregroundColor(Pent.bad) : Text("")))
                    .font(.system(size: 12.5, weight: .semibold)).tracking(0.4)
                    .foregroundStyle(Pent.label2)
                    .padding(.horizontal, 4)
            }
            HStack(alignment: multiline ? .top : .center, spacing: 9) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 16))
                        .foregroundStyle(Pent.label3)
                        .padding(.top, multiline ? 2 : 0)
                }
                field
                    .font(.pentBody)
                    .foregroundStyle(Pent.label)
                    .tint(Pent.accent)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(autocap)
                    .autocorrectionDisabled(disableAutocorrect)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, multiline ? 13 : 0)
            .frame(minHeight: multiline ? 84 : 48, alignment: multiline ? .topLeading : .leading)
            .background(Pent.surface, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .strokeBorder(error != nil ? Pent.bad : Pent.separator, lineWidth: error != nil ? 1.5 : 0.5)
            )
            if let error, !error.isEmpty {
                Text(error).font(.pentFoot).foregroundStyle(Pent.bad).padding(.horizontal, 4)
            }
        }
    }

    @ViewBuilder private var field: some View {
        if secure {
            SecureField(placeholder, text: $text)
        } else if multiline {
            TextField(placeholder, text: $text, axis: .vertical).lineLimit(3 ... 6)
        } else {
            TextField(placeholder, text: $text)
        }
    }
}
