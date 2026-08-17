import Foundation
import Shared

/// Generates expenses from recurring rules.
///
/// Android does this in a WorkManager `PeriodicWorkRequest` that only ever asks "is this
/// rule due *today*?". If the worker doesn't run on the due day the occurrence is missed
/// permanently, because `lastGeneratedDate` only advances when it fires.
///
/// iOS can't rely on background execution at all — `BGTaskScheduler` is best-effort and may
/// not run for days — so this runs on launch and catches up on any missed days.
///
/// The day-selection logic itself lives in `shared`
/// (`RecurringScheduleCalculator`) and is used by Android's worker too, so both platforms
/// generate identical expenses from the same rule. Android has since been moved onto the
/// same catch-up behaviour.
struct RecurringExpenseService {
    private let firestoreService: FirestoreService

    init(firestoreService: FirestoreService) {
        self.firestoreService = firestoreService
    }

    @discardableResult
    func generateDueExpenses(householdId: String) async throws -> Int {
        let rules = try await firestoreService.getRecurringExpenses(householdId: householdId)
        let now = Date().epochMillis
        var created = 0

        for rule in rules where rule.isActive {
            // Which days are due is decided in shared/ so this and the Android worker cannot
            // drift — they read and write the same lastGeneratedDate in Firestore.
            let dueDays = RecurringScheduleCalculator.shared.dueDaysInDeviceZone(
                rule: rule,
                todayMillis: now
            )

            for day in dueDays {
                let expense = Expense(
                    id: UUID().uuidString,
                    householdId: rule.householdId,
                    amount: rule.amount,
                    categoryId: rule.categoryId,
                    categoryName: rule.categoryName,
                    date: Date(epochMillis: day.int64Value),
                    notes: rule.notes.isEmpty ? "Recurring" : "Recurring: \(rule.notes)",
                    addedBy: rule.addedBy,
                    addedByName: rule.addedByName,
                    paymentMethod: rule.paymentMethod
                )
                try await firestoreService.addExpense(householdId: householdId, expense: expense)
                created += 1
            }

            // Advance the watermark even when nothing was due, so the next launch doesn't
            // re-walk the same window.
            let watermark = dueDays.last?.int64Value ?? now
            try await firestoreService.updateRecurringExpense(
                householdId: householdId,
                recurring: rule.withLastGenerated(Date(epochMillis: watermark))
            )
        }
        return created
    }
}

extension RecurringExpense {
    var frequencyLabel: String {
        switch frequency {
        case RecurringFrequency.daily: return "Daily"
        case RecurringFrequency.weekly: return "Weekly"
        case RecurringFrequency.monthly: return "Monthly"
        case RecurringFrequency.yearly: return "Yearly"
        default: return "—"
        }
    }

    func withLastGenerated(_ date: Date) -> RecurringExpense {
        RecurringExpense(
            id: id,
            householdId: householdId,
            amount: amount,
            categoryId: categoryId,
            categoryName: categoryName,
            notes: notes,
            addedBy: addedBy,
            addedByName: addedByName,
            frequency: frequency,
            dayOfWeek: dayOfWeek,
            dayOfMonth: dayOfMonth,
            monthOfYear: monthOfYear,
            startDate: startDate,
            endDate: endDate,
            lastGeneratedDate: KotlinLong(longLong: date.epochMillis),
            isActive: isActive,
            paymentMethod: paymentMethod,
            createdAt: createdAt
        )
    }

    func withActive(_ active: Bool) -> RecurringExpense {
        RecurringExpense(
            id: id, householdId: householdId, amount: amount, categoryId: categoryId,
            categoryName: categoryName, notes: notes, addedBy: addedBy, addedByName: addedByName,
            frequency: frequency, dayOfWeek: dayOfWeek, dayOfMonth: dayOfMonth,
            monthOfYear: monthOfYear, startDate: startDate, endDate: endDate,
            lastGeneratedDate: lastGeneratedDate, isActive: active,
            paymentMethod: paymentMethod, createdAt: createdAt
        )
    }
}
