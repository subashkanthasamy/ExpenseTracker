import Foundation
import FirebaseFirestore
import Shared

@MainActor
@Observable
class ExpenseListViewModel {
    var expenses: [Expense] = []
    var isLoading = true

    // Filter state. Held as separate properties so SwiftUI can bind to each one, then
    // assembled into the shared criteria on read.
    var searchQuery = ""
    var categoryFilter: String?
    var personFilter: String?
    var dateRange: DateRangeFilter = .all

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var listener: ListenerRegistration?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    var criteria: ExpenseFilterCriteria {
        ExpenseFilterCriteria.of(
            searchQuery: searchQuery,
            personFilter: personFilter,
            categoryFilter: categoryFilter,
            dateRange: dateRange
        )
    }

    var isFiltering: Bool { criteria.isActive }

    /// Everything except search, which has its own visible field — this is what the filter
    /// button highlights on.
    var hasChipFilters: Bool {
        dateRange != .all || categoryFilter != nil || personFilter != nil
    }

    /// Filtering goes through the shared engine rather than a Swift copy of the predicate, so
    /// the same filter gives the same rows here as on Android.
    var filteredExpenses: [Expense] {
        ExpenseFilter.shared.apply(
            expenses: expenses,
            criteria: criteria,
            nowMillis: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }

    var categoryOptions: [FilterOption] { ExpenseFilter.shared.categoryOptions(expenses: expenses) }

    var personOptions: [FilterOption] { ExpenseFilter.shared.personOptions(expenses: expenses) }

    /// Sorted explicitly rather than inheriting Firestore's `order(by:)` — relying on the
    /// query's ordering silently reshuffles sections and rows if the query ever changes.
    var groupedExpenses: [(String, [Expense])] {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM dd, yyyy"
        let grouped = Dictionary(grouping: filteredExpenses) { formatter.string(from: $0.dateValue) }
        return grouped
            .map { ($0.key, $0.value.sorted { $0.date > $1.date }) }
            .sorted { ($0.1.first?.date ?? 0) > ($1.1.first?.date ?? 0) }
    }

    func clearFilters() {
        searchQuery = ""
        categoryFilter = nil
        personFilter = nil
        dateRange = .all
    }

    func load() async {
        // The view model outlives the view inside a TabView, so `.task` runs again on every
        // reappearance. Without this guard each one attached another snapshot listener.
        guard listener == nil else { return }

        guard let hid = await authService.getActiveHouseholdId() else {
            print("ExpenseList: No household ID")
            isLoading = false
            return
        }
        print("ExpenseList: Observing expenses for \(hid)")
        listener = firestoreService.observeExpenses(householdId: hid) { [weak self] expenses in
            Task { @MainActor in
                self?.expenses = expenses
                self?.isLoading = false
            }
        }
    }

    func delete(_ expense: Expense) async {
        guard let hid = await authService.getActiveHouseholdId() else { return }
        try? await firestoreService.deleteExpense(householdId: hid, expenseId: expense.id)
    }

    func cleanup() {
        listener?.remove()
        listener = nil
    }
}

@Observable
class AddEditExpenseViewModel {
    var amount = ""
    var selectedCategory: Shared.Category?
    var categories: [Shared.Category] = []
    var date = Date()
    var notes = ""
    var isEditing = false
    var isLoading = false
    var error: String?

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var editingExpenseId: String?
    private var householdId: String?

    init(authService: AuthService, firestoreService: FirestoreService, expenseId: String? = nil) {
        self.authService = authService
        self.firestoreService = firestoreService
        self.editingExpenseId = expenseId
        if expenseId != nil { isEditing = true }
    }

    func loadCategories() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            print("AddEditExpense: No household ID")
            return
        }
        householdId = hid

        var cats = (try? await firestoreService.getCategories(householdId: hid)) ?? []

        // Deduplicate by name (keep first occurrence)
        var seen = Set<String>()
        cats = cats.filter { seen.insert($0.name).inserted }

        // Only seed if no presets exist
        if cats.filter({ $0.isPreset }).isEmpty {
            await seedPresetCategories(householdId: hid)
            cats = (try? await firestoreService.getCategories(householdId: hid)) ?? []
            seen.removeAll()
            cats = cats.filter { seen.insert($0.name).inserted }
        }

        categories = cats
        print("AddEditExpense: Loaded \(cats.count) categories")

        if let eid = editingExpenseId {
            if let expenses = try? await firestoreService.getExpenses(householdId: hid),
               let expense = expenses.first(where: { $0.id == eid }) {
                amount = String(expense.amount)
                selectedCategory = categories.first { $0.id == expense.categoryId }
                date = expense.dateValue
                notes = expense.notes
            }
        }
    }

    func save() async -> Bool {
        var resolvedHid = householdId
        if resolvedHid == nil {
            resolvedHid = await authService.getActiveHouseholdId()
        }
        guard let hid = resolvedHid,
              let uid = authService.currentUserId,
              let amt = Double(amount), amt > 0,
              let cat = selectedCategory else {
            error = "Please fill amount and select a category"
            return false
        }
        isLoading = true
        let expense = Expense(
            id: editingExpenseId ?? UUID().uuidString, householdId: hid,
            amount: amt, categoryId: cat.id, categoryName: cat.name, date: date,
            notes: notes, addedBy: uid, addedByName: authService.currentUserDisplayName ?? "User",
            createdAt: isEditing ? date : Date(), updatedAt: Date()
        )
        do {
            if isEditing {
                try await firestoreService.updateExpense(householdId: hid, expense: expense)
            } else {
                try await firestoreService.addExpense(householdId: hid, expense: expense)
            }
            print("AddEditExpense: Saved expense \(expense.id) to \(hid)")
            isLoading = false
            return true
        } catch {
            print("AddEditExpense save error: \(error)")
            self.error = error.localizedDescription
            isLoading = false
            return false
        }
    }

    private func seedPresetCategories(householdId: String) async {
        let presets: [(String, String)] = [
            ("Food", "🍔"), ("Groceries", "🛒"), ("Transport", "🚗"),
            ("Entertainment", "🎬"), ("Shopping", "🛍️"), ("Bills", "📱"),
            ("Health", "🏥"), ("Education", "📚"), ("Rent", "🏠"),
            ("Travel", "✈️"), ("Insurance", "🛡️"), ("Gifts", "🎁"),
            ("Fitness", "💪"), ("Misc", "💳")
        ]
        for (name, icon) in presets {
            let id = "preset_\(name.lowercased())"
            let cat = Category(id: id, name: name, icon: icon,
                             color: 0xFF7B61FF, isPreset: true, householdId: householdId)
            try? await firestoreService.addCategory(householdId: householdId, category: cat)
        }
    }
}
