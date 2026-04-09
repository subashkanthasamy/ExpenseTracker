import Foundation
import FirebaseFirestore

@Observable
class ExpenseListViewModel {
    var expenses: [Expense] = []
    var isLoading = true
    var searchQuery = ""

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var listener: ListenerRegistration?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    var filteredExpenses: [Expense] {
        if searchQuery.isEmpty { return expenses }
        return expenses.filter {
            $0.categoryName.localizedCaseInsensitiveContains(searchQuery) ||
            $0.notes.localizedCaseInsensitiveContains(searchQuery)
        }
    }

    var groupedExpenses: [(String, [Expense])] {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM dd, yyyy"
        let grouped = Dictionary(grouping: filteredExpenses) { formatter.string(from: $0.date) }
        return grouped.sorted { $0.value.first!.date > $1.value.first!.date }
    }

    func load() async {
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

    func cleanup() { listener?.remove() }
}

@Observable
class AddEditExpenseViewModel {
    var amount = ""
    var selectedCategory: Category?
    var categories: [Category] = []
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
                date = expense.date
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
