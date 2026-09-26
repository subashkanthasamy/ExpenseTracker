import Foundation
import Shared

@MainActor
@Observable
class RecurringViewModel {
    var items: [RecurringExpense] = []
    var categories: [Shared.Category] = []
    var isLoading = true
    var error: String?
    /// How many expenses the catch-up pass created on this load.
    var generatedCount = 0

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private let recurringService: RecurringExpenseService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
        self.recurringService = RecurringExpenseService(firestoreService: firestoreService)
    }

    func load() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            isLoading = false
            return
        }
        isLoading = true
        do {
            // Catch up first so the list reflects anything just generated.
            generatedCount = try await recurringService.generateDueExpenses(householdId: hid)
            items = try await firestoreService.getRecurringExpenses(householdId: hid)
        } catch {
            self.error = "Couldn't load recurring expenses. Check your connection and try again."
        }
        isLoading = false
    }

    func loadCategories() async {
        guard let hid = await authService.getActiveHouseholdId() else { return }
        categories = (try? await firestoreService.getCategories(householdId: hid)) ?? []
    }

    func add(
        amount: Double,
        category: Shared.Category?,
        notes: String,
        frequencyIndex: Int,
        dayOfMonth: Int,
        weekday: Int,
        startDate: Date,
        paymentMethod: PaymentMethod
    ) async {
        guard let hid = await authService.getActiveHouseholdId(), let category else { return }
        let all = RecurringFrequency.entries
        let frequency = frequencyIndex < all.count ? all[frequencyIndex] : RecurringFrequency.monthly
        let cal = Calendar.current

        let rule = RecurringExpense(
            id: UUID().uuidString,
            householdId: hid,
            amount: amount,
            categoryId: category.id,
            categoryName: category.name,
            notes: notes,
            addedBy: authService.currentUserId ?? "",
            addedByName: authService.currentUserDisplayName ?? "Me",
            frequency: frequency,
            dayOfWeek: frequency == RecurringFrequency.weekly ? KotlinInt(int: Int32(weekday)) : nil,
            dayOfMonth: (frequency == RecurringFrequency.monthly || frequency == RecurringFrequency.yearly)
                ? KotlinInt(int: Int32(dayOfMonth)) : nil,
            monthOfYear: frequency == RecurringFrequency.yearly
                ? KotlinInt(int: Int32(cal.component(.month, from: startDate))) : nil,
            startDate: cal.startOfDay(for: startDate).epochMillis,
            endDate: nil,
            // Start the watermark the day before, so a rule due today fires immediately.
            lastGeneratedDate: KotlinLong(
                longLong: (cal.date(byAdding: .day, value: -1, to: cal.startOfDay(for: startDate)) ?? startDate).epochMillis
            ),
            isActive: true,
            paymentMethod: paymentMethod,
            createdAt: Date().epochMillis
        )
        do {
            try await firestoreService.addRecurringExpense(householdId: hid, recurring: rule)
            await load()
        } catch {
            self.error = "Couldn't save the recurring expense. Check your connection and try again."
        }
    }

    func setActive(_ active: Bool, for rule: RecurringExpense) async {
        guard let hid = await authService.getActiveHouseholdId() else { return }
        do {
            try await firestoreService.updateRecurringExpense(householdId: hid, recurring: rule.withActive(active))
            items = try await firestoreService.getRecurringExpenses(householdId: hid)
        } catch {
            self.error = "Couldn't update the recurring expense. Check your connection and try again."
        }
    }

    func delete(_ rule: RecurringExpense) async {
        guard let hid = await authService.getActiveHouseholdId() else { return }
        do {
            try await firestoreService.deleteRecurringExpense(householdId: hid, id: rule.id)
            items.removeAll { $0.id == rule.id }
        } catch {
            self.error = "Couldn't delete the recurring expense. Check your connection and try again."
        }
    }
}
