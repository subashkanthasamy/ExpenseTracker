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

    func load() {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        listener = firestoreService.observeExpenses(householdId: hid) { [weak self] expenses in
            Task { @MainActor in self?.expenses = expenses; self?.isLoading = false }
        }
    }

    func delete(_ expense: Expense) async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
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

    init(authService: AuthService, firestoreService: FirestoreService, expenseId: String? = nil) {
        self.authService = authService
        self.firestoreService = firestoreService
        self.editingExpenseId = expenseId
        if expenseId != nil { isEditing = true }
    }

    func loadCategories() async {
        guard let hid = authService.currentUser?.activeHouseholdId else { return }
        categories = (try? await firestoreService.getCategories(householdId: hid)) ?? []
        if let eid = editingExpenseId {
            if let expenses = try? await firestoreService.getExpenses(householdId: hid),
               let expense = expenses.first(where: { $0.id == eid }) {
                await MainActor.run {
                    amount = String(expense.amount)
                    selectedCategory = categories.first { $0.id == expense.categoryId }
                    date = expense.date
                    notes = expense.notes
                }
            }
        }
    }

    func save() async -> Bool {
        guard let hid = authService.currentUser?.activeHouseholdId,
              let uid = authService.currentUserId,
              let amt = Double(amount), amt > 0,
              let cat = selectedCategory else {
            error = "Please fill all required fields"; return false
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
            isLoading = false; return true
        } catch {
            self.error = error.localizedDescription; isLoading = false; return false
        }
    }
}
