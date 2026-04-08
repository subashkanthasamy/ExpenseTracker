import Foundation
import FirebaseFirestore

// MARK: - Category

@Observable
class CategoryViewModel {
    var presetCategories: [Category] = []
    var customCategories: [Category] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var listener: ListenerRegistration?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        listener = firestoreService.observeCategories(householdId: hid) { [weak self] cats in
            Task { @MainActor in
                self?.presetCategories = cats.filter { $0.isPreset }
                self?.customCategories = cats.filter { !$0.isPreset }
                self?.isLoading = false
            }
        }
    }

    func addCategory(name: String, icon: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        let cat = Category(id: UUID().uuidString, name: name, icon: icon, color: 0xFF7B61FF, isPreset: false, householdId: hid)
        try? await firestoreService.addCategory(householdId: hid, category: cat)
    }

    func deleteCategory(_ id: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        try? await firestoreService.deleteCategory(householdId: hid, categoryId: id)
    }

    func cleanup() { listener?.remove() }
}

// MARK: - Budget

@Observable
class BudgetViewModel {
    var budgets: [Budget] = []
    var categories: [Category] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        async let budgetsFetch = firestoreService.getBudgets(householdId: hid)
        async let categoriesFetch = firestoreService.getCategories(householdId: hid)
        async let expensesFetch = firestoreService.getExpenses(householdId: hid)
        do {
            var b = try await budgetsFetch
            let cats = try await categoriesFetch
            let expenses = try await expensesFetch
            let cal = Calendar.current
            let now = Date()
            let monthStart = cal.date(from: cal.dateComponents([.year, .month], from: now))!
            let thisMonthExpenses = expenses.filter { $0.date >= monthStart }
            for i in b.indices {
                b[i].spent = thisMonthExpenses.filter { $0.categoryId == b[i].categoryId }.reduce(0) { $0 + $1.amount }
            }
            await MainActor.run { self.budgets = b; self.categories = cats; isLoading = false }
        } catch {}
    }

    func addBudget(categoryId: String, categoryName: String, limit: Double) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        let budget = Budget(id: UUID().uuidString, householdId: hid, categoryId: categoryId, categoryName: categoryName, monthlyLimit: limit)
        try? await firestoreService.addBudget(householdId: hid, budget: budget)
        await load()
    }

    func deleteBudget(_ id: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        try? await firestoreService.deleteBudget(householdId: hid, budgetId: id)
        await load()
    }
}

// MARK: - Net Worth

@Observable
class NetWorthViewModel {
    var assets: [Asset] = []
    var liabilities: [Liability] = []
    var totalAssets: Double = 0
    var totalLiabilities: Double = 0
    var netWorth: Double = 0
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        do {
            let a = try await firestoreService.getAssets(householdId: hid)
            let l = try await firestoreService.getLiabilities(householdId: hid)
            await MainActor.run {
                assets = a; liabilities = l
                totalAssets = a.reduce(0) { $0 + $1.value }
                totalLiabilities = l.reduce(0) { $0 + $1.amount }
                netWorth = totalAssets - totalLiabilities
                isLoading = false
            }
        } catch {}
    }

    func addAsset(name: String, value: Double, type: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId, let uid = authService.currentUserId else { return }
        let asset = Asset(id: UUID().uuidString, householdId: hid, name: name, value: value, type: type, date: Date(), addedBy: uid)
        try? await firestoreService.addAsset(householdId: hid, asset: asset)
        await load()
    }

    func deleteAsset(_ id: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        try? await firestoreService.deleteAsset(householdId: hid, assetId: id)
        await load()
    }

    func addLiability(name: String, amount: Double, type: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId, let uid = authService.currentUserId else { return }
        let liability = Liability(id: UUID().uuidString, householdId: hid, name: name, amount: amount, type: type, date: Date(), addedBy: uid)
        try? await firestoreService.addLiability(householdId: hid, liability: liability)
        await load()
    }

    func deleteLiability(_ id: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        try? await firestoreService.deleteLiability(householdId: hid, liabilityId: id)
        await load()
    }
}

// MARK: - Savings

@Observable
class SavingsViewModel {
    var goals: [SavingsGoal] = []
    var isLoading = true

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        goals = (try? await firestoreService.getSavingsGoals(householdId: hid)) ?? []
        isLoading = false
    }

    func addGoal(name: String, target: Double, icon: String, targetDate: Date?) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        let goal = SavingsGoal(id: UUID().uuidString, householdId: hid, name: name, targetAmount: target,
                               icon: icon, targetDate: targetDate, createdAt: Date())
        try? await firestoreService.addSavingsGoal(householdId: hid, goal: goal)
        await load()
    }

    func addContribution(goalId: String, amount: Double) async {
        guard let hid = authService.currentUser?.activeHouseholdId,
              var goal = goals.first(where: { $0.id == goalId }) else { return }
        goal.currentAmount += amount
        try? await firestoreService.updateSavingsGoal(householdId: hid, goal: goal)
        await load()
    }

    func deleteGoal(_ id: String) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        try? await firestoreService.deleteSavingsGoal(householdId: hid, goalId: id)
        await load()
    }
}

// MARK: - Insights

@Observable
class InsightsViewModel {
    var categoryBreakdown: [String: Double] = [:]
    var dailySpending: [String: Double] = [:]
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
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        do {
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            let cal = Calendar.current
            let now = Date()
            let monthStart = cal.date(from: cal.dateComponents([.year, .month], from: now))!
            let lastMonthStart = cal.date(byAdding: .month, value: -1, to: monthStart)!

            let thisMonth = expenses.filter { $0.date >= monthStart }
            let lastMonth = expenses.filter { $0.date >= lastMonthStart && $0.date < monthStart }

            var catMap: [String: Double] = [:]
            for e in thisMonth { catMap[e.categoryName, default: 0] += e.amount }

            let formatter = DateFormatter()
            formatter.dateFormat = "EEE"
            var daily: [String: Double] = [:]
            for e in thisMonth { daily[formatter.string(from: e.date), default: 0] += e.amount }

            await MainActor.run {
                totalSpent = thisMonth.reduce(0) { $0 + $1.amount }
                lastPeriodSpent = lastMonth.reduce(0) { $0 + $1.amount }
                categoryBreakdown = catMap
                dailySpending = daily
                topCategory = catMap.max(by: { $0.value < $1.value })?.key ?? ""
                isLoading = false
            }
        } catch {}
    }
}

// MARK: - Household

@Observable
class HouseholdViewModel {
    var household: Household?
    var households: [Household] = []
    var members: [AppUser] = []
    var isLoading = true
    var error: String?

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func load() async {
        guard let uid = authService.currentUserId,
              let hid = authService.currentUser?.activeHouseholdId else { return }
        do {
            household = try await firestoreService.getHousehold(hid)
            households = try await firestoreService.getUserHouseholds(userId: uid)
            isLoading = false
        } catch {}
    }

    func deleteHousehold() async {
        guard let hid = household?.id else { return }
        try? await firestoreService.deleteHousehold(hid)
        await load()
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
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        let expenses = (try? await firestoreService.getExpenses(householdId: hid)) ?? []
        let cal = Calendar.current
        let monthStart = cal.date(from: cal.dateComponents([.year, .month], from: Date()))!
        let thisMonth = expenses.filter { $0.date >= monthStart }
        totalExpenses = thisMonth.reduce(0) { $0 + $1.amount }
        var catMap: [String: Double] = [:]
        for e in thisMonth { catMap[e.categoryName, default: 0] += e.amount }
        if let top = catMap.max(by: { $0.value < $1.value }) {
            topCategory = top.key; topCategoryAmount = top.value
        }

        let welcomeMsg = ChatMessage(id: UUID().uuidString,
            text: "Hi! I'm your Financial Coach. This month you've spent \(formatCurrency(totalExpenses)). How can I help?",
            isUser: false, timestamp: Date())
        await MainActor.run { messages = [welcomeMsg] }
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
            return "Your financial score is \(financialScore)/100. Keep tracking expenses consistently to improve it!"
        }
        if lower.contains("spend") || lower.contains("overspend") {
            return "Your top spending is \(topCategory) at \(formatCurrency(topCategoryAmount)). That's \(Int(topCategoryAmount / max(totalExpenses, 1) * 100))% of your total."
        }
        if lower.contains("invest") {
            return "Consider the 50/30/20 rule: 50% needs, 30% wants, 20% savings/investment. Track your categories to see where you stand!"
        }
        return "This month you've spent \(formatCurrency(totalExpenses)). Your biggest category is \(topCategory). Ask me about saving, spending, or your score!"
    }
}
