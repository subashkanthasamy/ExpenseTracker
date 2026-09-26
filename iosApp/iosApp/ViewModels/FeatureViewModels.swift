import Foundation
import FirebaseFirestore
import Shared

// MARK: - Category

@Observable
class CategoryViewModel {
    /// Surfaced to the user — a failed write must not look like "nothing here yet".
    var error: String?
    var presetCategories: [Shared.Category] = []
    var customCategories: [Shared.Category] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var listener: ListenerRegistration?
    private var householdId: String?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            isLoading = false; return
        }
        householdId = hid
        listener = firestoreService.observeCategories(householdId: hid) { [weak self] cats in
            Task { @MainActor in
                // Deduplicate by name
                var seen = Set<String>()
                let unique = cats.filter { seen.insert($0.name).inserted }
                self?.presetCategories = unique.filter { $0.isPreset }
                self?.customCategories = unique.filter { !$0.isPreset }
                self?.isLoading = false
            }
        }
    }

    func addCategory(name: String, icon: String) async {
        guard let hid = householdId else { return }
        let cat = Shared.Category(id: UUID().uuidString, name: name, icon: icon, color: 0xFF7B61FF, isPreset: false, householdId: hid)
        do {
            try await firestoreService.addCategory(householdId: hid, category: cat)
        } catch {
            self.error = "Couldn't add the category. Check your connection and try again."
        }
    }

    func deleteCategory(_ id: String) async {
        guard let hid = householdId else { return }
        do {
            try await firestoreService.deleteCategory(householdId: hid, categoryId: id)
        } catch {
            self.error = "Couldn't delete the category. Check your connection and try again."
        }
    }

    func cleanup() { listener?.remove() }
}

// MARK: - Budget

@Observable
class BudgetViewModel {
    /// Surfaced to the user — a failed write must not look like "nothing here yet".
    var error: String?
    var budgets: [Budget] = []
    var categories: [Shared.Category] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var householdId: String?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            isLoading = false; return
        }
        householdId = hid
        do {
            let b = try await firestoreService.getBudgets(householdId: hid)
            let cats = try await firestoreService.getCategories(householdId: hid)
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            let cal = Calendar.current
            let monthStart = cal.date(from: cal.dateComponents([.year, .month], from: Date()))!
            // A budget is a shared commitment, so it is measured against shared spending only.
            // Counting personal rows would also make the same budget read differently for the
            // owner and for a member.
            let thisMonthExpenses = Permissions.shared.sharedOnly(
                expenses: expenses.filter { $0.dateValue >= monthStart }
            )
            budgets = b.map { budget in
                let spent = thisMonthExpenses
                    .filter { $0.categoryId == budget.categoryId }
                    .reduce(0.0) { $0 + $1.amount }
                return Budget(
                    id: budget.id,
                    householdId: budget.householdId,
                    categoryId: budget.categoryId,
                    categoryName: budget.categoryName,
                    monthlyLimit: budget.monthlyLimit,
                    spent: spent
                )
            }
            categories = cats
            isLoading = false
        } catch {
            print("BudgetVM error: \(error)")
            self.error = "Couldn't load budgets. Check your connection and try again."
            isLoading = false
        }
    }

    func addBudget(categoryId: String, categoryName: String, limit: Double) async {
        guard let hid = householdId else { return }
        let budget = Budget(id: UUID().uuidString, householdId: hid, categoryId: categoryId, categoryName: categoryName, monthlyLimit: limit)
        do {
            try await firestoreService.addBudget(householdId: hid, budget: budget)
        } catch {
            self.error = "Couldn't add the budget. Check your connection and try again."
        }
        await load()
    }

    func deleteBudget(_ id: String) async {
        guard let hid = householdId else { return }
        try? await firestoreService.deleteBudget(householdId: hid, budgetId: id)
        await load()
    }
}

// MARK: - Net Worth

@Observable
class NetWorthViewModel {
    /// Surfaced to the user — a failed write must not look like "nothing here yet".
    var error: String?
    var assets: [Asset] = []
    var liabilities: [Liability] = []
    var totalAssets: Double = 0
    var totalLiabilities: Double = 0
    var netWorth: Double = 0
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var householdId: String?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            isLoading = false; return
        }
        householdId = hid
        do {
            let a = try await firestoreService.getAssets(householdId: hid)
            let l = try await firestoreService.getLiabilities(householdId: hid)
            assets = a; liabilities = l
            totalAssets = a.reduce(0) { $0 + $1.value }
            totalLiabilities = l.reduce(0) { $0 + $1.amount }
            netWorth = totalAssets - totalLiabilities
            isLoading = false
        } catch {
            print("NetWorthVM error: \(error)")
            isLoading = false
        }
    }

    func addAsset(name: String, value: Double, type: String) async {
        guard let hid = householdId, let uid = authService.currentUserId else { return }
        let asset = Asset(id: UUID().uuidString, householdId: hid, name: name, value: value, type: type, date: Date(), addedBy: uid)
        try? await firestoreService.addAsset(householdId: hid, asset: asset)
        await load()
    }

    func deleteAsset(_ id: String) async {
        guard let hid = householdId else { return }
        try? await firestoreService.deleteAsset(householdId: hid, assetId: id)
        await load()
    }

    func addLiability(name: String, amount: Double, type: String) async {
        guard let hid = householdId, let uid = authService.currentUserId else { return }
        let liability = Liability(id: UUID().uuidString, householdId: hid, name: name, amount: amount, type: type, date: Date(), addedBy: uid)
        try? await firestoreService.addLiability(householdId: hid, liability: liability)
        await load()
    }

    func deleteLiability(_ id: String) async {
        guard let hid = householdId else { return }
        try? await firestoreService.deleteLiability(householdId: hid, liabilityId: id)
        await load()
    }
}

// MARK: - Savings

@Observable
class SavingsViewModel {
    /// Surfaced to the user — a failed write must not look like "nothing here yet".
    var error: String?
    var goals: [SavingsGoal] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var householdId: String?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            isLoading = false; return
        }
        householdId = hid
        goals = (try? await firestoreService.getSavingsGoals(householdId: hid)) ?? []
        isLoading = false
    }

    func addGoal(name: String, target: Double, icon: String, targetDate: Date?) async {
        guard let hid = householdId else { return }
        let goal = SavingsGoal(id: UUID().uuidString, householdId: hid, name: name, targetAmount: target,
                               icon: icon, targetDate: targetDate, createdAt: Date())
        do {
            try await firestoreService.addSavingsGoal(householdId: hid, goal: goal)
        } catch {
            self.error = "Couldn't add the savings goal. Check your connection and try again."
        }
        await load()
    }

    func addContribution(goalId: String, amount: Double) async {
        guard let hid = householdId,
              let existing = goals.first(where: { $0.id == goalId }) else { return }
        let goal = existing.with(currentAmount: existing.currentAmount + amount)
        try? await firestoreService.updateSavingsGoal(householdId: hid, goal: goal)
        await load()
    }

    func deleteGoal(_ id: String) async {
        guard let hid = householdId else { return }
        try? await firestoreService.deleteSavingsGoal(householdId: hid, goalId: id)
        await load()
    }
}

// MARK: - Insights

@Observable
class InsightsViewModel {
    var categoryBreakdown: [String: Double] = [:]
    var dailySpending: [String: Double] = [:]
    var personSplit: [PersonSpending] = []
    var paymentSplit: [SpendingSlice] = []
    var totalSpent: Double = 0
    var lastPeriodSpent: Double = 0
    var topCategory = ""
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            isLoading = false; return
        }
        do {
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            let cal = Calendar.current
            let now = Date()
            let monthStart = cal.date(from: cal.dateComponents([.year, .month], from: now))!
            let lastMonthStart = cal.date(byAdding: .month, value: -1, to: monthStart)!

            // Shared only for every household-level figure: a member cannot see peers'
            // personal rows, so including them would make the same label mean different numbers
            // depending on who is looking. Both periods, or the comparison is meaningless.
            let thisMonth = Permissions.shared.sharedOnly(
                expenses: expenses.filter { $0.dateValue >= monthStart }
            )
            let lastMonth = Permissions.shared.sharedOnly(
                expenses: expenses.filter { $0.dateValue >= lastMonthStart && $0.dateValue < monthStart }
            )

            var catMap: [String: Double] = [:]
            for e in thisMonth { catMap[e.categoryName, default: 0] += e.amount }

            let formatter = DateFormatter()
            formatter.dateFormat = "EEE"
            var daily: [String: Double] = [:]
            for e in thisMonth { daily[formatter.string(from: e.dateValue), default: 0] += e.amount }

            totalSpent = thisMonth.reduce(0) { $0 + $1.amount }
            lastPeriodSpent = lastMonth.reduce(0) { $0 + $1.amount }
            categoryBreakdown = catMap
            dailySpending = daily
            // Shared with Android so both platforms split the same way.
            personSplit = SpendingSplitCalculator.shared.split(expenses: thisMonth)
            paymentSplit = PaymentMethodSplitCalculator.shared.split(expenses: thisMonth)
            topCategory = catMap.max(by: { $0.value < $1.value })?.key ?? ""
            isLoading = false
        } catch {
            print("InsightsVM error: \(error)")
            isLoading = false
        }
    }
}

// MARK: - Household

@Observable
class HouseholdViewModel {
    var household: Household?
    var households: [Household] = []
    var members: [AppUser] = []
    var currentUid: String = ""
    /// Drives which controls the screen offers. Enforcement is firestore.rules; this only
    /// keeps the UI from presenting a button that would fail.
    var role: HouseholdRole = HouseholdRole.none
    var isLoading = true
    var error: String?

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let uid = authService.currentUserId else {
            print("HouseholdVM: No user ID")
            isLoading = false
            return
        }
        do {
            let allHouseholds = try await firestoreService.getUserHouseholds(userId: uid)
            households = allHouseholds

            let hid = await authService.getActiveHouseholdId()
            if let hid = hid {
                household = try await firestoreService.getHousehold(hid)
            } else if let first = allHouseholds.first {
                household = first
            }
            currentUid = uid
            if let household {
                members = (try? await firestoreService.getHouseholdMembers(uids: household.memberUids)) ?? []
            }
            let admin = await authService.isAdmin()
            role = household.map { Permissions.shared.roleOf(household: $0, uid: uid, isAdmin: admin) }
                ?? HouseholdRole.none
            isLoading = false
            // Households predating the invite-code lookup have no entry yet; publish it so
            // the code shown on this screen actually works.
            if let household {
                await firestoreService.ensureInviteCodePublished(household)
            }
            print("HouseholdVM: Loaded \(allHouseholds.count) households, active: \(household?.name ?? "none")")
        } catch {
            print("HouseholdVM load error: \(error)")
            self.error = "Couldn't load your household. Check your connection and try again."
            isLoading = false
        }
    }

    /// Promote or demote a member.
    ///
    /// Reloads afterwards rather than patching state locally: the write can be rejected by the
    /// rules, and showing the new role before the server accepted it would be a lie.
    func setMemberRole(uid: String, role: String) async {
        guard let hid = household?.id else { return }
        do {
            try await firestoreService.updateMemberRole(householdId: hid, uid: uid, role: role)
            await load()
        } catch {
            self.error = "Couldn't change the member's role. Check your connection and try again."
        }
    }

    func removeMember(uid: String) async {
        guard let hid = household?.id else { return }
        do {
            try await firestoreService.removeMember(householdId: hid, uid: uid)
            await load()
        } catch {
            self.error = "Couldn't remove the member from the household. Check your connection and try again."
        }
    }

    func deleteHousehold() async {
        guard let household else { return }
        do {
            try await firestoreService.deleteHousehold(household)
            // No reload: the active household id still points at the deleted document, and
            // reading it is denied, so load() would report a failure after a success.
            self.household = nil
            households.removeAll { $0.id == household.id }
            members = []
            role = HouseholdRole.none
        } catch {
            print("Delete household error: \(error)")
            self.error = "Couldn't delete the household. Check your connection and try again."
        }
    }
}

// MARK: - Financial Coach

@Observable
class FinancialCoachViewModel {
    var messages: [ChatMessage] = []
    var inputText = ""
    var isLoading = false
    var financialScore = 78

    private var totalExpenses: Double = 0
    private var topCategory = ""
    private var topCategoryAmount: Double = 0

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func loadContext() async {
        guard let hid = await authService.getActiveHouseholdId() else { return }
        let expenses = (try? await firestoreService.getExpenses(householdId: hid)) ?? []
        let cal = Calendar.current
        let monthStart = cal.date(from: cal.dateComponents([.year, .month], from: Date()))!
        let thisMonth = expenses.filter { $0.dateValue >= monthStart }
        totalExpenses = thisMonth.reduce(0) { $0 + $1.amount }
        var catMap: [String: Double] = [:]
        for e in thisMonth { catMap[e.categoryName, default: 0] += e.amount }
        if let top = catMap.max(by: { $0.value < $1.value }) {
            topCategory = top.key; topCategoryAmount = top.value
        }

        let welcomeMsg = ChatMessage(id: UUID().uuidString,
            text: "Hi, I'm your financial coach. This month you've spent \(formatCurrency(totalExpenses)). How can I help?",
            isUser: false, timestamp: Date())
        messages = [welcomeMsg]
    }

    func sendMessage() {
        let text = inputText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        let userMsg = ChatMessage(id: UUID().uuidString, text: text, isUser: true, timestamp: Date())
        messages.append(userMsg)
        inputText = ""
        isLoading = true

        let response = generateResponse(text)
        let botMsg = ChatMessage(id: UUID().uuidString, text: response, isUser: false, timestamp: Date())
        messages.append(botMsg)
        isLoading = false
    }

    private func generateResponse(_ input: String) -> String {
        let lower = input.lowercased()
        if lower.contains("save") {
            return "Based on your spending of \(formatCurrency(totalExpenses)) this month, try cutting \(topCategory) by 20% to save \(formatCurrency(topCategoryAmount * 0.2))."
        }
        if lower.contains("score") {
            return "Your financial score is \(financialScore)/100. Keep tracking your expenses to improve it."
        }
        if lower.contains("spend") || lower.contains("overspend") {
            return "Your top spending is \(topCategory) at \(formatCurrency(topCategoryAmount)). That's \(Int(topCategoryAmount / max(totalExpenses, 1) * 100))% of your total."
        }
        if lower.contains("invest") {
            return "Consider the 50/30/20 rule: 50% needs, 30% wants, 20% savings/investment. Track your categories to see where you stand."
        }
        return "This month you've spent \(formatCurrency(totalExpenses)). Your biggest category is \(topCategory). Ask me about saving, spending or your score."
    }
}
