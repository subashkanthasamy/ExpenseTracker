import Foundation
import FirebaseFirestore
import Shared

nonisolated(unsafe) class FirestoreService: @unchecked Sendable {
    private let db = Firestore.firestore()

    // MARK: - Time field encoding
    //
    // Time fields on the wire are epoch milliseconds — that is what the shared
    // domain model declares and what the Android app reads and writes. This app
    // used to store Firestore Timestamps instead, which crashed Android
    // ("Field 'date' is not a java.lang.Number") and, in the other direction,
    // silently decoded every Android-written date as "now" because the
    // `as? Timestamp` cast failed and fell through to Date().
    //
    // Write millis; read millis but still accept Timestamps so documents written
    // by older builds keep working.

    fileprivate static func encodeMillis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    fileprivate static func decodeMillis(_ value: Any?) -> Date? {
        if let number = value as? NSNumber {
            return Date(timeIntervalSince1970: number.doubleValue / 1000)
        }
        if let timestamp = value as? Timestamp {
            return timestamp.dateValue()
        }
        return nil
    }

    // MARK: - Household

    func createHousehold(_ household: Household) async throws {
        try await db.collection("households").document(household.id).setData([
            "name": household.name,
            "ownerUid": household.ownerUid,
            "memberUids": household.memberUids,
            // Empty at creation: the rules require the creator to grant nobody else a role.
            "roles": household.roles,
            "inviteCode": household.inviteCode,
            "createdAt": household.createdAt
        ])
        // Written second: the rules require the author to already be a member.
        try await publishInviteCode(
            householdId: household.id,
            householdName: household.name,
            inviteCode: household.inviteCode
        )
    }

    /// What an invite code resolves to, without needing read access to the household.
    struct InviteTarget {
        let householdId: String
        let householdName: String
    }

    /// Publishes the code -> household lookup read by `resolveInviteCode`.
    func publishInviteCode(householdId: String, householdName: String, inviteCode: String) async throws {
        try await db.collection("inviteCodes").document(inviteCode).setData([
            "householdId": householdId,
            "householdName": householdName
        ])
    }

    /// Resolves an invite code.
    ///
    /// Replaces the old `households` query on inviteCode, which required every household to
    /// be readable by any signed-in user — that allowed enumerating households, reading
    /// their codes and joining them. This lookup only discloses anything to someone who
    /// already knows the code.
    func resolveInviteCode(_ code: String) async throws -> InviteTarget? {
        let doc = try await db.collection("inviteCodes").document(code).getDocument()
        guard doc.exists, let data = doc.data(), let householdId = data["householdId"] as? String else {
            return nil
        }
        return InviteTarget(householdId: householdId, householdName: data["householdName"] as? String ?? "")
    }

    /// Publishes the lookup document for `household` if it is missing.
    ///
    /// Households created before the invite-code change have no `inviteCodes/{code}` entry,
    /// so they cannot be joined until one exists. A member self-heals it when they open the
    /// household screen — the only place the code is shown to share.
    func ensureInviteCodePublished(_ household: Household) async {
        guard !household.inviteCode.isEmpty else { return }
        do {
            let existing = try await db.collection("inviteCodes").document(household.inviteCode).getDocument()
            if existing.exists {
                // Never repoint a code that already belongs to a different household.
                if (existing.data()?["householdId"] as? String) != household.id {
                    print("inviteCode \(household.inviteCode) already maps elsewhere — leaving it alone")
                }
                return
            }
            try await publishInviteCode(
                householdId: household.id,
                householdName: household.name,
                inviteCode: household.inviteCode
            )
        } catch {
            print("Could not publish invite code lookup: \(error)")
        }
    }

    /// Adds the current user with arrayUnion, so joining needs no read access to the
    /// household — which the rules no longer grant to non-members.
    ///
    /// `memberUids` and `roles` move together: the first is what the "my households" query
    /// reads, the second is what the rules trust. The join branch rejects an update that
    /// touches only one of them.
    func addSelfToHousehold(_ householdId: String, uid: String) async throws {
        try await db.collection("households").document(householdId)
            .updateData([
                "memberUids": FieldValue.arrayUnion([uid]),
                "roles.\(uid)": "member"
            ])
    }

    func getHousehold(_ id: String) async throws -> Household? {
        let doc = try await db.collection("households").document(id).getDocument()
        return doc.exists ? decodeHousehold(doc) : nil
    }


    /// Profiles for a household's members.
    ///
    /// The rules allow reading a user document only if you share a household with them, which
    /// is exactly this case. Missing documents are skipped rather than failing the whole list.
    func getHouseholdMembers(uids: [String]) async throws -> [AppUser] {
        var users: [AppUser] = []
        for uid in uids {
            let doc = try? await db.collection("users").document(uid).getDocument()
            guard let d = doc?.data() else { continue }
            users.append(AppUser(
                uid: uid,
                email: d["email"] as? String ?? "",
                displayName: d["displayName"] as? String ?? "(no name)"
            ))
        }
        return users
    }

    /// Owner-only. Only `roles.<uid>` changes; `memberUids` already contains them.
    func updateMemberRole(householdId: String, uid: String, role: String) async throws {
        try await db.collection("households").document(householdId)
            .updateData(["roles.\(uid)": role])
    }

    /// Owner-only. Both fields move together — the rules reject an update that leaves
    /// `memberUids` and `roles` disagreeing.
    func removeMember(householdId: String, uid: String) async throws {
        try await db.collection("households").document(householdId)
            .updateData([
                "memberUids": FieldValue.arrayRemove([uid]),
                "roles.\(uid)": FieldValue.delete()
            ])
    }

    func updateHouseholdMembers(_ id: String, members: [String]) async throws {
        try await db.collection("households").document(id).updateData(["memberUids": members])
    }

    func deleteHousehold(_ id: String) async throws {
        try await db.collection("households").document(id).delete()
    }

    func getUserHouseholds(userId: String) async throws -> [Household] {
        let snap = try await db.collection("households").whereField("memberUids", arrayContains: userId).getDocuments()
        return snap.documents.compactMap { decodeHousehold($0) }
    }

    // MARK: - Expenses

    func getExpenses(householdId: String) async throws -> [Expense] {
        let snap = try await expensesCollection(householdId).order(by: "date", descending: true).getDocuments()
        return snap.documents.compactMap { decodeExpense($0) }
    }

    func addExpense(householdId: String, expense: Expense) async throws {
        try await expensesCollection(householdId).document(expense.id).setData(encodeExpense(expense))
    }

    func updateExpense(householdId: String, expense: Expense) async throws {
        try await expensesCollection(householdId).document(expense.id).setData(encodeExpense(expense))
    }

    func deleteExpense(householdId: String, expenseId: String) async throws {
        try await expensesCollection(householdId).document(expenseId).delete()
    }

    func observeExpenses(householdId: String, onChange: @escaping ([Expense]) -> Void) -> ListenerRegistration {
        expensesCollection(householdId).order(by: "date", descending: true).addSnapshotListener { snap, _ in
            let expenses = snap?.documents.compactMap { self.decodeExpense($0) } ?? []
            onChange(expenses)
        }
    }

    // MARK: - Categories

    func getCategories(householdId: String) async throws -> [Shared.Category] {
        let snap = try await categoriesCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeCategory($0) }
    }

    func addCategory(householdId: String, category: Shared.Category) async throws {
        try await categoriesCollection(householdId).document(category.id).setData(encodeCategory(category))
    }

    func deleteCategory(householdId: String, categoryId: String) async throws {
        try await categoriesCollection(householdId).document(categoryId).delete()
    }

    func observeCategories(householdId: String, onChange: @escaping ([Shared.Category]) -> Void) -> ListenerRegistration {
        categoriesCollection(householdId).addSnapshotListener { snap, _ in
            let categories = snap?.documents.compactMap { self.decodeCategory($0) } ?? []
            onChange(categories)
        }
    }

    // MARK: - Assets & Liabilities

    func getAssets(householdId: String) async throws -> [Asset] {
        let snap = try await assetsCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeAsset($0) }
    }

    func addAsset(householdId: String, asset: Asset) async throws {
        try await assetsCollection(householdId).document(asset.id).setData(encodeAsset(asset))
    }

    func deleteAsset(householdId: String, assetId: String) async throws {
        try await assetsCollection(householdId).document(assetId).delete()
    }

    func getLiabilities(householdId: String) async throws -> [Liability] {
        let snap = try await liabilitiesCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeLiability($0) }
    }

    func addLiability(householdId: String, liability: Liability) async throws {
        try await liabilitiesCollection(householdId).document(liability.id).setData(encodeLiability(liability))
    }

    func deleteLiability(householdId: String, liabilityId: String) async throws {
        try await liabilitiesCollection(householdId).document(liabilityId).delete()
    }

    // MARK: - Budgets

    func getBudgets(householdId: String) async throws -> [Budget] {
        let snap = try await budgetsCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeBudget($0) }
    }

    func addBudget(householdId: String, budget: Budget) async throws {
        try await budgetsCollection(householdId).document(budget.id).setData(encodeBudget(budget))
    }

    func deleteBudget(householdId: String, budgetId: String) async throws {
        try await budgetsCollection(householdId).document(budgetId).delete()
    }

    // MARK: - Savings Goals

    func getSavingsGoals(householdId: String) async throws -> [SavingsGoal] {
        let snap = try await savingsCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeSavingsGoal($0) }
    }

    func addSavingsGoal(householdId: String, goal: SavingsGoal) async throws {
        try await savingsCollection(householdId).document(goal.id).setData(encodeSavingsGoal(goal))
    }

    func updateSavingsGoal(householdId: String, goal: SavingsGoal) async throws {
        try await savingsCollection(householdId).document(goal.id).setData(encodeSavingsGoal(goal))
    }

    func deleteSavingsGoal(householdId: String, goalId: String) async throws {
        try await savingsCollection(householdId).document(goalId).delete()
    }

    // MARK: - Collection refs

    private func expensesCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("expenses")
    }
    private func categoriesCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("categories")
    }
    private func assetsCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("assets")
    }
    private func liabilitiesCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("liabilities")
    }
    private func budgetsCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("budgets")
    }
    private func savingsCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("savingsGoals")
    }

    private func recurringCollection(_ hid: String) -> CollectionReference {
        db.collection("households").document(hid).collection("recurring")
    }

    // MARK: - Recurring expenses
    //
    // NOTE: Android keeps recurring rules in Room only and never syncs them, so rules
    // created here are not visible to the Android app (and vice versa) until Android is
    // moved onto this collection too.

    func getRecurringExpenses(householdId: String) async throws -> [RecurringExpense] {
        let snap = try await recurringCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeRecurring($0) }
    }

    func addRecurringExpense(householdId: String, recurring: RecurringExpense) async throws {
        try await recurringCollection(householdId).document(recurring.id).setData(encodeRecurring(recurring))
    }

    func updateRecurringExpense(householdId: String, recurring: RecurringExpense) async throws {
        try await recurringCollection(householdId).document(recurring.id).setData(encodeRecurring(recurring))
    }

    func deleteRecurringExpense(householdId: String, id: String) async throws {
        try await recurringCollection(householdId).document(id).delete()
    }

    private func decodeRecurring(_ doc: DocumentSnapshot) -> RecurringExpense? {
        guard let d = doc.data() else { return nil }
        func intOrNil(_ key: String) -> KotlinInt? {
            (d[key] as? NSNumber).map { KotlinInt(int: $0.int32Value) }
        }
        func longOrNil(_ key: String) -> KotlinLong? {
            Self.decodeMillis(d[key]).map { KotlinLong(longLong: $0.epochMillis) }
        }
        return RecurringExpense(
            id: doc.documentID,
            householdId: d["householdId"] as? String ?? "",
            amount: d["amount"] as? Double ?? 0,
            categoryId: d["categoryId"] as? String ?? "",
            categoryName: d["categoryName"] as? String ?? "",
            notes: d["notes"] as? String ?? "",
            addedBy: d["addedBy"] as? String ?? "",
            addedByName: d["addedByName"] as? String ?? "",
            frequency: Self.frequency(from: d["frequency"] as? Int ?? 0),
            dayOfWeek: intOrNil("dayOfWeek"),
            dayOfMonth: intOrNil("dayOfMonth"),
            monthOfYear: intOrNil("monthOfYear"),
            startDate: (Self.decodeMillis(d["startDate"]) ?? Date()).epochMillis,
            endDate: longOrNil("endDate"),
            lastGeneratedDate: longOrNil("lastGeneratedDate"),
            isActive: d["isActive"] as? Bool ?? true,
            createdAt: (Self.decodeMillis(d["createdAt"]) ?? Date()).epochMillis
        )
    }

    private func encodeRecurring(_ r: RecurringExpense) -> [String: Any] {
        var data: [String: Any] = [
            "householdId": r.householdId,
            "amount": r.amount,
            "categoryId": r.categoryId,
            "categoryName": r.categoryName,
            "notes": r.notes,
            "addedBy": r.addedBy,
            "addedByName": r.addedByName,
            "frequency": Int(r.frequency.ordinal),
            "startDate": r.startDate,
            "isActive": r.isActive,
            "createdAt": r.createdAt,
        ]
        if let dow = r.dayOfWeek { data["dayOfWeek"] = dow.intValue }
        if let dom = r.dayOfMonth { data["dayOfMonth"] = dom.intValue }
        if let moy = r.monthOfYear { data["monthOfYear"] = moy.intValue }
        if let end = r.endDate { data["endDate"] = end.int64Value }
        if let last = r.lastGeneratedDate { data["lastGeneratedDate"] = last.int64Value }
        return data
    }

    private static func frequency(from ordinal: Int) -> RecurringFrequency {
        let all = RecurringFrequency.entries
        guard ordinal >= 0, ordinal < all.count else { return RecurringFrequency.monthly }
        return all[ordinal]
    }

    // MARK: - Encoders/Decoders

    private func decodeHousehold(_ doc: DocumentSnapshot) -> Household? {
        guard let d = doc.data() else { return nil }
        return Household(
            id: doc.documentID,
            name: d["name"] as? String ?? "",
            memberUids: d["memberUids"] as? [String] ?? [],
            ownerUid: d["ownerUid"] as? String ?? "",
            roles: d["roles"] as? [String: String] ?? [:],
            inviteCode: d["inviteCode"] as? String ?? "",
            createdAt: Self.decodeMillis(d["createdAt"]) ?? Date()
        )
    }

    private func decodeExpense(_ doc: DocumentSnapshot) -> Expense? {
        guard let d = doc.data() else { return nil }
        return Expense(
            id: doc.documentID,
            householdId: d["householdId"] as? String ?? "",
            amount: d["amount"] as? Double ?? 0,
            categoryId: d["categoryId"] as? String ?? "",
            categoryName: d["categoryName"] as? String ?? "",
            date: Self.decodeMillis(d["date"]) ?? Date(),
            notes: d["notes"] as? String ?? "",
            addedBy: d["addedBy"] as? String ?? "",
            addedByName: d["addedByName"] as? String ?? "",
            createdAt: Self.decodeMillis(d["createdAt"]) ?? Date(),
            updatedAt: Self.decodeMillis(d["updatedAt"]) ?? Date()
        )
    }

    private func encodeExpense(_ e: Expense) -> [String: Any] {
        ["householdId": e.householdId, "amount": e.amount, "categoryId": e.categoryId,
         "categoryName": e.categoryName, "date": e.date, "notes": e.notes,
         "addedBy": e.addedBy, "addedByName": e.addedByName,
         "createdAt": e.createdAt, "updatedAt": e.updatedAt]
    }

    private func decodeCategory(_ doc: DocumentSnapshot) -> Shared.Category? {
        guard let d = doc.data() else { return nil }
        return Shared.Category(id: doc.documentID, name: d["name"] as? String ?? "",
                        icon: d["icon"] as? String ?? "", color: d["color"] as? Int64 ?? 0,
                        isPreset: d["isPreset"] as? Bool ?? false, householdId: d["householdId"] as? String ?? "")
    }

    private func encodeCategory(_ c: Shared.Category) -> [String: Any] {
        ["name": c.name, "icon": c.icon, "color": c.color, "isPreset": c.isPreset, "householdId": c.householdId]
    }

    private func decodeAsset(_ doc: DocumentSnapshot) -> Asset? {
        guard let d = doc.data() else { return nil }
        return Asset(id: doc.documentID, householdId: d["householdId"] as? String ?? "",
                     name: d["name"] as? String ?? "", value: d["value"] as? Double ?? 0,
                     type: d["type"] as? String ?? "", date: Self.decodeMillis(d["date"]) ?? Date(),
                     addedBy: d["addedBy"] as? String ?? "")
    }

    private func encodeAsset(_ a: Asset) -> [String: Any] {
        ["householdId": a.householdId, "name": a.name, "value": a.value, "type": a.type,
         "date": a.date, "addedBy": a.addedBy]
    }

    private func decodeLiability(_ doc: DocumentSnapshot) -> Liability? {
        guard let d = doc.data() else { return nil }
        return Liability(id: doc.documentID, householdId: d["householdId"] as? String ?? "",
                         name: d["name"] as? String ?? "", amount: d["amount"] as? Double ?? 0,
                         type: d["type"] as? String ?? "", date: Self.decodeMillis(d["date"]) ?? Date(),
                         addedBy: d["addedBy"] as? String ?? "")
    }

    private func encodeLiability(_ l: Liability) -> [String: Any] {
        ["householdId": l.householdId, "name": l.name, "amount": l.amount, "type": l.type,
         "date": l.date, "addedBy": l.addedBy]
    }

    private func decodeBudget(_ doc: DocumentSnapshot) -> Budget? {
        guard let d = doc.data() else { return nil }
        return Budget(id: doc.documentID, householdId: d["householdId"] as? String ?? "",
                      categoryId: d["categoryId"] as? String ?? "", categoryName: d["categoryName"] as? String ?? "",
                      monthlyLimit: d["monthlyLimit"] as? Double ?? 0)
    }

    private func encodeBudget(_ b: Budget) -> [String: Any] {
        ["householdId": b.householdId, "categoryId": b.categoryId, "categoryName": b.categoryName, "monthlyLimit": b.monthlyLimit]
    }

    private func decodeSavingsGoal(_ doc: DocumentSnapshot) -> SavingsGoal? {
        guard let d = doc.data() else { return nil }
        return SavingsGoal(id: doc.documentID, householdId: d["householdId"] as? String ?? "",
                           name: d["name"] as? String ?? "", targetAmount: d["targetAmount"] as? Double ?? 0,
                           currentAmount: d["currentAmount"] as? Double ?? 0, icon: d["icon"] as? String ?? "🏯",
                           targetDate: Self.decodeMillis(d["targetDate"]),
                           createdAt: Self.decodeMillis(d["createdAt"]) ?? Date())
    }

    private func encodeSavingsGoal(_ g: SavingsGoal) -> [String: Any] {
        var data: [String: Any] = ["householdId": g.householdId, "name": g.name, "targetAmount": g.targetAmount,
                                    "currentAmount": g.currentAmount, "icon": g.icon,
                                    "createdAt": g.createdAt]
        if let td = g.targetDate { data["targetDate"] = td.int64Value }
        return data
    }
}
