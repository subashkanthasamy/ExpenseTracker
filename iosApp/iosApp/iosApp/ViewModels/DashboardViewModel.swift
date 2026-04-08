import Foundation
import FirebaseFirestore
import SwiftUI

@Observable
class DashboardViewModel {
    var monthTotal: Double = 0
    var lastMonthTotal: Double = 0
    var recentExpenses: [Expense] = []
    var categoryBreakdown: [CategoryBreakdown] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var listener: ListenerRegistration?

    private let categoryColors: [Color] = [
        AppColors.accentPurple, AppColors.accentOrange, AppColors.incomeGreen,
        AppColors.gradientPink, .blue, .cyan, .yellow, .brown
    ]

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        listener = firestoreService.observeExpenses(householdId: hid) { [weak self] expenses in
            Task { @MainActor in self?.processExpenses(expenses) }
        }
    }

    @MainActor
    private func processExpenses(_ expenses: [Expense]) {
        let cal = Calendar.current
        let now = Date()
        let currentMonth = cal.component(.month, from: now)
        let currentYear = cal.component(.year, from: now)

        let thisMonth = expenses.filter {
            cal.component(.month, from: $0.date) == currentMonth &&
            cal.component(.year, from: $0.date) == currentYear
        }
        let lastMonth = expenses.filter {
            let m = cal.component(.month, from: $0.date)
            let y = cal.component(.year, from: $0.date)
            if currentMonth == 1 { return m == 12 && y == currentYear - 1 }
            return m == currentMonth - 1 && y == currentYear
        }

        monthTotal = thisMonth.reduce(0) { $0 + $1.amount }
        lastMonthTotal = lastMonth.reduce(0) { $0 + $1.amount }
        recentExpenses = Array(thisMonth.prefix(5))

        // Category breakdown
        var catMap: [String: Double] = [:]
        for e in thisMonth { catMap[e.categoryName, default: 0] += e.amount }
        let total = max(monthTotal, 1)
        categoryBreakdown = catMap.sorted { $0.value > $1.value }.prefix(6).enumerated().map { i, kv in
            CategoryBreakdown(categoryName: kv.key, amount: kv.value,
                            percentage: Float(kv.value / total * 100),
                            color: categoryColors[i % categoryColors.count])
        }
        isLoading = false
    }

    func cleanup() { listener?.remove() }
}
