import Foundation
import FirebaseFirestore
import SwiftUI
import Shared

@Observable
class DashboardViewModel {
    var monthTotal: Double = 0
    var lastMonthTotal: Double = 0
    var recentExpenses: [Expense] = []
    var categoryBreakdown: [CategoryBreakdown] = []
    var isLoading = true
    var noHousehold = false

    private let authService: AuthService
    private let firestoreService: FirestoreService
    /// Members get two listeners, managers one — see FirestoreService.observeExpenses.
    private var listeners: [ListenerRegistration] = []
    /// Set before `load()`; selects the query shape. Defaults to the restricted path so a
    /// missing role can only under-fetch, never trigger a rejected unfiltered query.
    var canReadAllExpenses = false

    private let categoryColors: [Color] = [
        AppColors.accentPurple, AppColors.accentOrange, AppColors.incomeGreen,
        AppColors.gradientPink, .blue, .cyan, .yellow, .brown
    ]

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            print("Dashboard: No household ID found")
            noHousehold = true
            isLoading = false
            return
        }
        print("Dashboard: Loading expenses for household \(hid)")
        let uid = authService.currentUserId ?? ""
        listeners = firestoreService.observeExpenses(
            householdId: hid,
            canReadAll: canReadAllExpenses,
            uid: uid
        ) { [weak self] expenses in
            Task { @MainActor in
                print("Dashboard: Received \(expenses.count) expenses")
                self?.processExpenses(expenses)
            }
        }
    }

    @MainActor
    private func processExpenses(_ expenses: [Expense]) {
        let cal = Calendar.current
        let now = Date()
        let currentMonth = cal.component(.month, from: now)
        let currentYear = cal.component(.year, from: now)

        let thisMonth = expenses.filter {
            cal.component(.month, from: $0.dateValue) == currentMonth &&
            cal.component(.year, from: $0.dateValue) == currentYear
        }
        let lastMonth = expenses.filter {
            let m = cal.component(.month, from: $0.dateValue)
            let y = cal.component(.year, from: $0.dateValue)
            if currentMonth == 1 { return m == 12 && y == currentYear - 1 }
            return m == currentMonth - 1 && y == currentYear
        }

        // Household figures exclude personal rows. A member cannot see their peers' personal
        // expenses, so counting them would make the same label show a different number to the
        // owner than to a member — which is why the headline says "shared".
        let thisMonthShared = Permissions.shared.sharedOnly(expenses: thisMonth)
        let lastMonthShared = Permissions.shared.sharedOnly(expenses: lastMonth)

        monthTotal = thisMonthShared.reduce(0) { $0 + $1.amount }
        lastMonthTotal = lastMonthShared.reduce(0) { $0 + $1.amount }
        // Recent lists rows rather than a household figure, so a personal expense still shows;
        // ExpenseRow marks it with a lock, so it is clear why it is not in the total above.
        recentExpenses = Array(thisMonth.sorted { $0.date > $1.date }.prefix(5))

        var catMap: [String: Double] = [:]
        for e in thisMonthShared { catMap[e.categoryName, default: 0] += e.amount }
        let total = max(monthTotal, 1)
        categoryBreakdown = catMap.sorted { $0.value > $1.value }.prefix(6).enumerated().map { i, kv in
            CategoryBreakdown(categoryName: kv.key, amount: kv.value,
                            percentage: Float(kv.value / total * 100),
                            color: categoryColors[i % categoryColors.count])
        }
        isLoading = false
    }

    func cleanup() {
        listeners.forEach { $0.remove() }
        listeners = []
    }
}
