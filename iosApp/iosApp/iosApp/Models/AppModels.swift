import Foundation

struct AppUser: Identifiable, Codable {
    var id: String { uid }
    let uid: String
    let email: String
    let displayName: String
    var householdIds: [String] = []
    var activeHouseholdId: String?
}

struct Household: Identifiable, Codable {
    let id: String
    let name: String
    var memberUids: [String]
    let inviteCode: String
    let createdAt: Date
}

struct Expense: Identifiable, Codable {
    let id: String
    let householdId: String
    let amount: Double
    let categoryId: String
    let categoryName: String
    let date: Date
    let notes: String
    let addedBy: String
    let addedByName: String
    let createdAt: Date
    let updatedAt: Date
    var isSynced: Bool = false
}

struct Category: Identifiable, Codable {
    let id: String
    let name: String
    let icon: String
    let color: Int64
    let isPreset: Bool
    let householdId: String
}

struct Budget: Identifiable, Codable {
    let id: String
    let householdId: String
    let categoryId: String
    let categoryName: String
    let monthlyLimit: Double
    var spent: Double = 0.0

    var percentage: Double { monthlyLimit > 0 ? (spent / monthlyLimit * 100) : 0 }
    var status: BudgetStatus {
        if percentage >= 100 { return .exceeded }
        if percentage >= 80 { return .warning }
        return .ok
    }
}

enum BudgetStatus { case ok, warning, exceeded }

struct Asset: Identifiable, Codable {
    let id: String
    let householdId: String
    let name: String
    let value: Double
    let type: String
    let date: Date
    let addedBy: String
}

struct Liability: Identifiable, Codable {
    let id: String
    let householdId: String
    let name: String
    let amount: Double
    let type: String
    let date: Date
    let addedBy: String
}

struct SavingsGoal: Identifiable, Codable {
    let id: String
    let householdId: String
    let name: String
    let targetAmount: Double
    var currentAmount: Double = 0
    var icon: String = "🏯"
    var targetDate: Date?
    let createdAt: Date

    var progress: Double { targetAmount > 0 ? min(currentAmount / targetAmount, 1.0) : 0 }
    var remaining: Double { max(targetAmount - currentAmount, 0) }
}

struct RecurringExpense: Identifiable, Codable {
    let id: String
    let householdId: String
    let amount: Double
    let categoryId: String
    let categoryName: String
    let notes: String
    let addedBy: String
    let addedByName: String
    let frequency: RecurringFrequency
    var dayOfWeek: Int?
    var dayOfMonth: Int?
    var startDate: Date
    var endDate: Date?
    var isActive: Bool = true
    let createdAt: Date
}

enum RecurringFrequency: Int, Codable, CaseIterable {
    case daily = 0, weekly = 1, monthly = 2, yearly = 3
    var label: String {
        switch self {
        case .daily: return "Daily"
        case .weekly: return "Weekly"
        case .monthly: return "Monthly"
        case .yearly: return "Yearly"
        }
    }
}

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

struct SpendingInsight: Identifiable {
    let id = UUID()
    let title: String
    let description: String
    let type: InsightType
    let relatedCategory: String?
    let percentageChange: Double?
}

enum InsightType { case trendUp, trendDown, suggestion, anomaly }

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

import SwiftUI
