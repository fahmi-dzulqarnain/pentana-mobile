//
//  PentanaTheme.swift
//  iosApp
//
//  Design tokens for the PENTANA iOS design (from the Claude Design handoff):
//  PENTANA orange + deep indigo brand, four domain semantics, full light/dark,
//  an HIG type scale, rounded money figures, and a native Liquid Glass layer.
//

import SwiftUI
import UIKit

// MARK: - Color helpers

extension Color {
    /// Solid color from a 0xRRGGBB hex.
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    /// Dynamic color that resolves per light/dark, with optional per-mode alpha.
    init(light: UInt, dark: UInt, lightAlpha: Double = 1, darkAlpha: Double = 1) {
        self = Color(uiColor: UIColor { trait in
            let isDark = trait.userInterfaceStyle == .dark
            let h = isDark ? dark : light
            let a = isDark ? darkAlpha : lightAlpha
            return UIColor(
                red: CGFloat((h >> 16) & 0xFF) / 255,
                green: CGFloat((h >> 8) & 0xFF) / 255,
                blue: CGFloat(h & 0xFF) / 255,
                alpha: CGFloat(a)
            )
        })
    }
}

// MARK: - Tokens

enum Pent {
    // Brand
    static let orange = Color(hex: 0xF7931E)
    static let orange600 = Color(hex: 0xE27D0A)
    static let indigo = Color(hex: 0x1B1464)
    static let indigo600 = Color(hex: 0x322670)
    static let onBrand = Color(hex: 0x2A1B00) // dark text on orange (AA)

    // Accent (brand action; AA on each surface)
    static let accent = Color(light: 0xE27D0A, dark: 0xFBB04C)
    static let accentSolid = Color(hex: 0xF7931E)

    // Domains
    static let dues = Color(light: 0x155EEF, dark: 0x6CA0FF)
    static let lunch = Color(light: 0xE27D0A, dark: 0xFBB04C)
    static let activ = Color(light: 0x0E8A53, dark: 0x34C77E)
    static let proof = Color(light: 0x6B4DE6, dark: 0xA78BFF)

    static let duesBg = Color(light: 0x155EEF, dark: 0x6CA0FF, lightAlpha: 0.12, darkAlpha: 0.18)
    static let lunchBg = Color(light: 0xF7931E, dark: 0xF7931E, lightAlpha: 0.16, darkAlpha: 0.20)
    static let activBg = Color(light: 0x149E61, dark: 0x2DC07E, lightAlpha: 0.14, darkAlpha: 0.18)
    static let proofBg = Color(light: 0x6B4DE6, dark: 0xA78BFF, lightAlpha: 0.14, darkAlpha: 0.20)

    // Status
    static let ok = Color(light: 0x0E8A53, dark: 0x34C77E)
    static let warn = Color(light: 0xC05E00, dark: 0xFBB04C)
    static let bad = Color(light: 0xD92D20, dark: 0xFF6B5E)
    static let neutral = Color(light: 0x6E6B82, dark: 0x9C99B4)

    static let okBg = Color(light: 0x149E61, dark: 0x2DC07E, lightAlpha: 0.14, darkAlpha: 0.18)
    static let warnBg = Color(light: 0xDC6803, dark: 0xFBB04C, lightAlpha: 0.15, darkAlpha: 0.18)
    static let badBg = Color(light: 0xD92D20, dark: 0xFF6B5E, lightAlpha: 0.12, darkAlpha: 0.18)
    static let neutralBg = Color(light: 0x6E6B82, dark: 0x9C99B4, lightAlpha: 0.12, darkAlpha: 0.16)

    // Labels
    static let label = Color(light: 0x14121F, dark: 0xF4F3F9)
    static let label2 = Color(light: 0x5B5870, dark: 0xADAAC2)
    static let label3 = Color(light: 0x8A879C, dark: 0x7E7B95)
    static let label4 = Color(light: 0xB7B5C4, dark: 0x56536B)

    // Surfaces
    static let bgGrouped = Color(light: 0xEFEFF4, dark: 0x0A0912)
    static let bgBase = Color(light: 0xF6F6FA, dark: 0x08070E)
    static let surface = Color(light: 0xFFFFFF, dark: 0x1A1826)
    static let surface2 = Color(light: 0xF2F2F7, dark: 0x232032)
    static let separator = Color(light: 0x3C375A, dark: 0xA09BC8, lightAlpha: 0.14, darkAlpha: 0.16)
    static let separatorOpaque = Color(light: 0xE3E2EC, dark: 0x2A2738)

    // Ambient field (behind Liquid Glass)
    static let field1 = Color(light: 0xEAE7FB, dark: 0x1A1448)
    static let field2 = Color(light: 0xFCEFD9, dark: 0x2A1838)
}

// MARK: - Type scale (HIG)

extension Font {
    static let pentLarge = Font.system(size: 32, weight: .bold)
    static let pentTitle1 = Font.system(size: 26, weight: .bold)
    static let pentTitle2 = Font.system(size: 21, weight: .bold)
    static let pentTitle3 = Font.system(size: 19, weight: .semibold)
    static let pentHeadline = Font.system(size: 16, weight: .semibold)
    static let pentBody = Font.system(size: 16, weight: .regular)
    static let pentBodyMedium = Font.system(size: 16, weight: .medium)
    static let pentCallout = Font.system(size: 15, weight: .regular)
    static let pentSub = Font.system(size: 14, weight: .regular)
    static let pentFoot = Font.system(size: 12.5, weight: .regular)
    static let pentCap = Font.system(size: 11.5, weight: .medium)

    /// Rounded, tabular figures for money & counts.
    static func pentMoney(_ size: CGFloat, weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight, design: .rounded).monospacedDigit()
    }
}

// MARK: - Liquid Glass

extension View {
    /// Native Liquid Glass surface clipped to a continuous rounded rect (iOS 26+).
    func pentGlass(_ cornerRadius: CGFloat = 20) -> some View {
        glassEffect(.regular, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

// MARK: - Ambient field background

/// Soft radial gradient that gives the Liquid Glass something to refract over.
struct AmbientBackground: View {
    var body: some View {
        ZStack {
            Pent.bgBase
            GeometryReader { geo in
                let w = geo.size.width
                let h = geo.size.height
                Pent.field1
                    .frame(width: w * 1.4, height: h * 0.7)
                    .blur(radius: 90)
                    .position(x: w * 0.9, y: h * 0.02)
                Pent.field2
                    .frame(width: w * 1.3, height: h * 0.6)
                    .blur(radius: 90)
                    .position(x: w * 0.0, y: h * 0.12)
            }
        }
        .ignoresSafeArea()
    }
}
