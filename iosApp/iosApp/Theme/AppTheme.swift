import SwiftUI
import UIKit
import Shared

/// Accents and semantic colours only — everything here is deliberately the same in both
/// appearances.
///
/// Surface and text colours live in `DS` (DesignSystem.swift) and are adaptive. The fixed
/// `backgroundLight` / `surfaceWhite` / `backgroundDark` / `surfaceDark` / `surfaceDarkElevated`
/// constants that used to sit here were unused by any view but were a standing trap: reaching
/// for one would pin a screen to a single appearance. Android had exactly that bug — a fixed
/// dark chat bubble and icon tile that stayed dark in light mode. Use `DS.card`,
/// `DS.elevated`, `DS.canvas` instead.
struct AppColors {
    static let gradientPurple = Color(hex: 0xFF7B61FF)
    static let gradientPink = Color(hex: 0xFFE040FB)
    static let gradientOrange = Color(hex: 0xFFFF8A65)

    static let incomeGreen = Color(hex: 0xFF4CAF50)
    static let expenseRed = Color(hex: 0xFFF44336)
    static let accentPurple = Color(hex: 0xFF7B61FF)
    static let accentOrange = Color(hex: 0xFFFF7043)
    static let overBudgetRed = Color(hex: 0xFFFF5252)
    static let savingsGreen = Color(hex: 0xFF66BB6A)

    static let gradient = LinearGradient(
        colors: [gradientPurple, gradientPink, gradientOrange],
        startPoint: .leading,
        endPoint: .trailing
    )
}

extension Color {
    /// Resolves per appearance, so a single token works in both light and dark mode.
    init(light: UInt, dark: UInt) {
        self = Color(UIColor { traits in
            UIColor(Color(hex: traits.userInterfaceStyle == .dark ? dark : light))
        })
    }

    init(hex: UInt) {
        let a = Double((hex >> 24) & 0xFF) / 255.0
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

func formatCurrency(_ amount: Double) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .currency
    formatter.currencySymbol = "₹"
    formatter.locale = Locale(identifier: "en_IN")
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: amount)) ?? "₹0.00"
}

func formatAmount(_ amount: Double) -> String {
    let abs = Swift.abs(amount)
    switch abs {
    case 10_000_000...: return "₹\(String(format: "%.1f", abs / 10_000_000))Cr"
    case 100_000...: return "₹\(String(format: "%.1f", abs / 100_000))L"
    case 1_000...: return "₹\(String(format: "%.1f", abs / 1_000))K"
    default: return formatCurrency(amount)
    }
}

/// SF Symbol name for a category.
///
/// Replaces the old emoji lookup. Emoji were drawn by the system emoji font, so the same
/// category rendered as different art on iOS and Android, could not be tinted to follow the
/// theme, and read to VoiceOver as "hamburger" rather than the category name. SF Symbols are
/// vectors that inherit `foregroundStyle` and align to the platform's optical grid.
///
/// The name→key decision lives in shared `CategoryIcons` so Android resolves identically;
/// only the key→symbol step below is platform-specific.
func categoryIcon(_ name: String, storedIcon: String? = nil) -> String {
    switch CategoryIcons.shared.resolve(icon: storedIcon, name: name) {
    case "restaurant": return "fork.knife"
    case "shopping_cart": return "cart.fill"
    case "directions_car": return "car.fill"
    case "home": return "house.fill"
    case "receipt_long": return "doc.text.fill"
    case "family_restroom": return "person.2.fill"
    case "movie": return "film.fill"
    case "shopping_bag": return "bag.fill"
    case "medical_services": return "cross.case.fill"
    case "school": return "book.fill"
    case "flight": return "airplane"
    case "shield": return "shield.fill"
    case "card_giftcard": return "gift.fill"
    case "fitness_center": return "figure.run"
    case "payments": return "indianrupeesign.circle.fill"
    case "trending_up": return "chart.line.uptrend.xyaxis"
    case "autorenew": return "arrow.triangle.2.circlepath"
    default: return "ellipsis.circle.fill"
    }
}
