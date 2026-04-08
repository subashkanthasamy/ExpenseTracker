import SwiftUI

struct AppColors {
    static let gradientPurple = Color(hex: 0xFF7B61FF)
    static let gradientPink = Color(hex: 0xFFE040FB)
    static let gradientOrange = Color(hex: 0xFFFF8A65)

    static let backgroundLight = Color(hex: 0xFFF5F5FA)
    static let surfaceWhite = Color.white
    static let backgroundDark = Color(hex: 0xFF121218)
    static let surfaceDark = Color(hex: 0xFF1E1E2A)
    static let surfaceDarkElevated = Color(hex: 0xFF252532)

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

func categoryEmoji(_ name: String) -> String {
    switch name.lowercased() {
    case "food": return "🍔"
    case "groceries": return "🛒"
    case "transport": return "🚗"
    case "entertainment": return "🎬"
    case "shopping": return "🛍️"
    case "bills": return "📱"
    case "health": return "🏥"
    case "education": return "📚"
    case "rent": return "🏠"
    case "salary", "income": return "💰"
    case "investment": return "📈"
    case "travel": return "✈️"
    case "insurance": return "🛡️"
    case "gifts": return "🎁"
    case "fitness": return "💪"
    default: return "💳"
    }
}
