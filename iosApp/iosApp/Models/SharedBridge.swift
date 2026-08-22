import Foundation
import Shared

// Bridges the KMP `Shared` domain models into idiomatic Swift.
//
// The Swift app used to declare its own copies of these models (AppModels.swift), which
// made the domain layer exist three times: shared/, app/, and here. That duplication
// caused real bugs — a Timestamp-vs-millis mismatch that crashed Android and silently
// corrupted dates on iOS. Shared is now the single source of truth.
//
// Three things need smoothing over, because Kotlin/Native's Objective-C export:
//   1. exports `Long` epoch millis, not `Date`
//   2. drops Kotlin default parameter values, so initialisers take every argument
//   3. makes every property read-only, with mutation via a full-argument `doCopy(...)`
//
// This file restores Date accessors, defaulted initialisers, and `with(...)` mutators.

// `User` would collide with `FirebaseAuth.User`; the app already calls it AppUser.
typealias AppUser = Shared.User

// MARK: - Epoch millis <-> Date

extension Date {
    init(epochMillis: Int64) {
        self.init(timeIntervalSince1970: Double(epochMillis) / 1000.0)
    }

    var epochMillis: Int64 {
        Int64((timeIntervalSince1970 * 1000).rounded())
    }
}

private func dateOrNil(_ millis: KotlinLong?) -> Date? {
    guard let millis else { return nil }
    return Date(epochMillis: millis.int64Value)
}

// MARK: - Identifiable

extension Expense: @retroactive Identifiable {}
extension Shared.Category: @retroactive Identifiable {}
extension Budget: @retroactive Identifiable {}
extension Household: @retroactive Identifiable {}
extension Asset: @retroactive Identifiable {}
extension Liability: @retroactive Identifiable {}
extension SavingsGoal: @retroactive Identifiable {}
extension RecurringExpense: @retroactive Identifiable {}

extension AppUser: @retroactive Identifiable {
    public var id: String { uid }
}

// MARK: - Date accessors
//
// Kotlin exposes these as Int64 millis; `…Value` is the Date form. Views and view models
// should use the Date form and let the boundary do the conversion.

extension Expense {
    var dateValue: Date { Date(epochMillis: date) }
    var createdAtValue: Date { Date(epochMillis: createdAt) }
    var updatedAtValue: Date { Date(epochMillis: updatedAt) }
}

extension Household {
    var createdAtValue: Date { Date(epochMillis: createdAt) }
}

extension Asset {
    var dateValue: Date { Date(epochMillis: date) }
}

extension Liability {
    var dateValue: Date { Date(epochMillis: date) }
}

extension SavingsGoal {
    var createdAtValue: Date { Date(epochMillis: createdAt) }
    var targetDateValue: Date? { dateOrNil(targetDate) }
}

extension RecurringExpense {
    var startDateValue: Date { Date(epochMillis: startDate) }
    var endDateValue: Date? { dateOrNil(endDate) }
    var lastGeneratedDateValue: Date? { dateOrNil(lastGeneratedDate) }
    var createdAtValue: Date { Date(epochMillis: createdAt) }
}

// MARK: - Date-based initialisers with Kotlin's defaults restored

extension Expense {
    convenience init(
        id: String,
        householdId: String,
        amount: Double,
        categoryId: String,
        categoryName: String,
        date: Date,
        notes: String = "",
        addedBy: String,
        addedByName: String,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        paymentMethod: PaymentMethod = PaymentMethod.unspecified,
        scope: ExpenseScope = ExpenseScope.shared,
        isSynced: Bool = false
    ) {
        self.init(
            id: id,
            householdId: householdId,
            amount: amount,
            categoryId: categoryId,
            categoryName: categoryName,
            date: date.epochMillis,
            notes: notes,
            addedBy: addedBy,
            addedByName: addedByName,
            createdAt: createdAt.epochMillis,
            updatedAt: updatedAt.epochMillis,
            paymentMethod: paymentMethod,
            scope: scope,
            isSynced: isSynced
        )
    }
}

extension Household {
    /// Kotlin's defaults for `ownerUid` / `roles` are not exported, so they are spelled out here.
    convenience init(
        id: String,
        name: String,
        memberUids: [String],
        ownerUid: String = "",
        roles: [String: String] = [:],
        inviteCode: String,
        createdAt: Date
    ) {
        self.init(
            id: id, name: name, memberUids: memberUids,
            ownerUid: ownerUid, roles: roles,
            inviteCode: inviteCode, createdAt: createdAt.epochMillis
        )
    }
}

extension Asset {
    convenience init(id: String, householdId: String, name: String, value: Double, type: String, date: Date, addedBy: String) {
        self.init(id: id, householdId: householdId, name: name, value: value, type: type, date: date.epochMillis, addedBy: addedBy)
    }
}

extension Liability {
    convenience init(id: String, householdId: String, name: String, amount: Double, type: String, date: Date, addedBy: String) {
        self.init(id: id, householdId: householdId, name: name, amount: amount, type: type, date: date.epochMillis, addedBy: addedBy)
    }
}

extension SavingsGoal {
    convenience init(
        id: String,
        householdId: String,
        name: String,
        targetAmount: Double,
        currentAmount: Double = 0,
        icon: String = "🎯",
        targetDate: Date? = nil,
        createdAt: Date = Date()
    ) {
        self.init(
            id: id,
            householdId: householdId,
            name: name,
            targetAmount: targetAmount,
            currentAmount: currentAmount,
            icon: icon,
            targetDate: targetDate.map { KotlinLong(longLong: $0.epochMillis) },
            createdAt: createdAt.epochMillis
        )
    }
}

extension Budget {
    convenience init(id: String, householdId: String, categoryId: String, categoryName: String, monthlyLimit: Double, spent: Double = 0) {
        self.init(id: id, householdId: householdId, categoryId: categoryId, categoryName: categoryName, monthlyLimit: monthlyLimit, spent: spent)
    }
}

extension ExpenseFilterCriteria {
    /// Kotlin default parameter values aren't exported, and a same-signature `convenience init`
    /// would collide with the generated one — so this is a factory rather than an initialiser.
    static func of(
        searchQuery: String = "",
        personFilter: String? = nil,
        categoryFilter: String? = nil,
        paymentMethodFilter: PaymentMethod? = nil,
        dateRange: DateRangeFilter = .all
    ) -> ExpenseFilterCriteria {
        ExpenseFilterCriteria(
            searchQuery: searchQuery,
            personFilter: personFilter,
            categoryFilter: categoryFilter,
            paymentMethodFilter: paymentMethodFilter,
            dateRange: dateRange
        )
    }
}

// MARK: - Mutation helpers
//
// Kotlin data classes are immutable across the boundary; `doCopy` needs every argument.

extension AppUser {
    /// Restores the Kotlin defaults for `householdIds` / `activeHouseholdId`.
    convenience init(uid: String, email: String, displayName: String) {
        self.init(uid: uid, email: email, displayName: displayName, householdIds: [], activeHouseholdId: nil)
    }

    func with(householdIds: [String]? = nil, activeHouseholdId: String?? = nil) -> AppUser {
        AppUser(
            uid: uid,
            email: email,
            displayName: displayName,
            householdIds: householdIds ?? self.householdIds,
            activeHouseholdId: activeHouseholdId ?? self.activeHouseholdId
        )
    }

    func addingHousehold(_ householdId: String, makeActive: Bool = true) -> AppUser {
        var ids = householdIds
        if !ids.contains(householdId) { ids.append(householdId) }
        return with(householdIds: ids, activeHouseholdId: makeActive ? .some(householdId) : nil)
    }
}

extension SavingsGoal {
    func with(currentAmount: Double) -> SavingsGoal {
        SavingsGoal(
            id: id,
            householdId: householdId,
            name: name,
            targetAmount: targetAmount,
            currentAmount: currentAmount,
            icon: icon,
            targetDate: targetDate,
            createdAt: createdAt
        )
    }
}
