import Foundation
import SwiftUI
import Shared

// Domain models (Expense, Category, Budget, Household, User, Asset, Liability,
// SavingsGoal, RecurringExpense, SpendingInsight and their enums) now come from the KMP
// `Shared` framework — see SharedBridge.swift. Only types that are genuinely iOS-side
// presentation concerns, or that have no shared counterpart yet, live here.

/// No shared counterpart yet — Android's reminders are not modelled in `shared/`.
struct Reminder: Identifiable, Codable {
    let id: String
    let householdId: String
    let title: String
    let type: Int
    let hour: Int
    let minute: Int
    var amount: Double?
    var dayOfMonth: Int?
    var repeatInterval: Int?
    var isEnabled: Bool = true
}

/// View-layer chat model for the Financial Coach. Carries SwiftUI-side presentation state,
/// so it deliberately stays local rather than using the shared ui/state equivalent.
struct ChatMessage: Identifiable {
    let id: String
    let text: String
    let isUser: Bool
    let timestamp: Date
    var inlineStats: [InlineStat]?
}

struct InlineStat {
    let emoji: String
    let label: String
    let value: String
    let isPositive: Bool
}

/// Holds a SwiftUI `Color`, which cannot live in shared Kotlin — stays local.
struct CategoryBreakdown: Identifiable {
    let id = UUID()
    let categoryName: String
    let amount: Double
    let percentage: Float
    let color: Color

    init(categoryName: String, amount: Double, percentage: Float, color: Color = .purple) {
        self.categoryName = categoryName
        self.amount = amount
        self.percentage = percentage
        self.color = color
    }
}
