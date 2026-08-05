import Foundation
import Shared

/// Generates expenses from recurring rules.
///
/// Android does this in a WorkManager `PeriodicWorkRequest` that only ever asks "is this
/// rule due *today*?". If the worker doesn't run on the due day the occurrence is missed
/// permanently, because `lastGeneratedDate` only advances when it fires.
///
/// iOS can't rely on background execution at all — `BGTaskScheduler` is best-effort and may
/// not run for days. So instead of porting the worker, this runs on launch and **catches
/// up**: it walks every day from the last generated date through today and creates an
/// expense for each day that was due. That is both robust on iOS and strictly better
/// behaviour than the Android original.
struct RecurringExpenseService {
    private let firestoreService: FirestoreService

    /// Guard against a pathological rule (e.g. a startDate years back) creating thousands
    /// of expenses in one pass.
    private let maxCatchUpDays = 400

    init(firestoreService: FirestoreService) {
        self.firestoreService = firestoreService
    }

    @discardableResult
    func generateDueExpenses(householdId: String) async throws -> Int {
        let rules = try await firestoreService.getRecurringExpenses(householdId: householdId)
        let cal = Calendar.current
        let todayStart = cal.startOfDay(for: Date())
        var created = 0

        for rule in rules where rule.isActive {
            if let end = rule.endDateValue, todayStart > cal.startOfDay(for: end) { continue }

            let startDay = cal.startOfDay(for: rule.startDateValue)
            if todayStart < startDay { continue }

            // Begin the day after the last generated day, or on the rule's start date.
            var cursor: Date
            if let last = rule.lastGeneratedDateValue {
                cursor = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: last)) ?? startDay
            } else {
                cursor = startDay
            }
            if cursor < startDay { cursor = startDay }

            // Don't walk further back than the cap.
            if let earliest = cal.date(byAdding: .day, value: -maxCatchUpDays, to: todayStart),
               cursor < earliest {
                cursor = earliest
            }

            var lastGenerated: Date?
            while cursor <= todayStart {
                if isDue(rule, on: cursor, calendar: cal) {
                    let expense = Expense(
                        id: UUID().uuidString,
                        householdId: rule.householdId,
                        amount: rule.amount,
                        categoryId: rule.categoryId,
                        categoryName: rule.categoryName,
                        date: cursor,
                        notes: rule.notes.isEmpty ? "Recurring" : "Recurring: \(rule.notes)",
                        addedBy: rule.addedBy,
                        addedByName: rule.addedByName
                    )
                    try await firestoreService.addExpense(householdId: householdId, expense: expense)
                    created += 1
                    lastGenerated = cursor
                }
                guard let next = cal.date(byAdding: .day, value: 1, to: cursor) else { break }
                cursor = next
            }

            // Always advance the watermark to today, so a rule that produced nothing this
            // pass isn't re-walked from scratch next launch.
            let watermark = lastGenerated ?? todayStart
            try await firestoreService.updateRecurringExpense(
                householdId: householdId,
                recurring: rule.withLastGenerated(watermark)
            )
        }
        return created
    }

    /// Mirrors RecurringExpenseWorker.isDue, including its end-of-month clamping so a rule
    /// set for the 31st still fires in February.
    private func isDue(_ rule: RecurringExpense, on day: Date, calendar cal: Calendar) -> Bool {
        let comps = cal.dateComponents([.day, .month, .weekday], from: day)
        guard let dom = comps.day, let month = comps.month, let weekday = comps.weekday else { return false }
        let lastDayOfMonth = cal.range(of: .day, in: .month, for: day)?.count ?? 31

        switch rule.frequency {
        case RecurringFrequency.daily:
            return true
        case RecurringFrequency.weekly:
            // Kotlin stores Calendar.DAY_OF_WEEK values (1 = Sunday), matching Foundation.
            return weekday == (rule.dayOfWeek?.intValue ?? 2)
        case RecurringFrequency.monthly:
            let target = rule.dayOfMonth?.intValue ?? 1
            return dom == target || (target > lastDayOfMonth && dom == lastDayOfMonth)
        case RecurringFrequency.yearly:
            let target = rule.dayOfMonth?.intValue ?? 1
            let targetMonth = rule.monthOfYear?.intValue ?? 1
            let dayMatch = dom == target || (target > lastDayOfMonth && dom == lastDayOfMonth)
            return dayMatch && month == targetMonth
        default:
            return false
        }
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
            createdAt: createdAt
        )
    }

    func withActive(_ active: Bool) -> RecurringExpense {
        RecurringExpense(
            id: id, householdId: householdId, amount: amount, categoryId: categoryId,
            categoryName: categoryName, notes: notes, addedBy: addedBy, addedByName: addedByName,
            frequency: frequency, dayOfWeek: dayOfWeek, dayOfMonth: dayOfMonth,
            monthOfYear: monthOfYear, startDate: startDate, endDate: endDate,
            lastGeneratedDate: lastGeneratedDate, isActive: active, createdAt: createdAt
        )
    }
}
