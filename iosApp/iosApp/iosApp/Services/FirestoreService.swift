import Foundation
import FirebaseFirestore

nonisolated(unsafe) class FirestoreService: @unchecked Sendable {
    private let db = Firestore.firestore()

    // MARK: - Household

    func createHousehold(_ household: Household) async throws {
        try await db.collection("households").document(household.id).setData([
            "name": household.name,
            "memberUids": household.memberUids,
            "inviteCode": household.inviteCode,
            "createdAt": Timestamp(date: household.createdAt)
        ])
    }

    func getHousehold(_ id: String) async throws -> Household? {
        let doc = try await db.collection("households").document(id).getDocument()
        return doc.exists ? decodeHousehold(doc) : nil
    }

    func getHouseholdByInviteCode(_ code: String) async throws -> Household? {
        let snap = try await db.collection("households").whereField("inviteCode", isEqualTo: code).limit(to: 1).getDocuments()
        return snap.documents.first.flatMap { decodeHousehold($0) }
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

    func getCategories(householdId: String) async throws -> [Category] {
        let snap = try await categoriesCollection(householdId).getDocuments()
        return snap.documents.compactMap { decodeCategory($0) }
    }

    func addCategory(householdId: String, category: Category) async throws {
        try await categoriesCollection(householdId).document(category.id).setData(encodeCategory(category))
    }

    func deleteCategory(householdId: String, categoryId: String) async throws {
        try await categoriesCollection(householdId).document(categoryId).delete()
    }

    func observeCategories(householdId: String, onChange: @escaping ([Category]) -> Void) -> ListenerRegistration {
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

    // MARK: - Encoders/Decoders

    private func decodeHousehold(_ doc: DocumentSnapshot) -> Household? {
        guard let d = doc.data() else { return nil }
        return Household(
            id: doc.documentID,
            name: d["name"] as? String ?? "",
            memberUids: d["memberUids"] as? [String] ?? [],
            inviteCode: d["inviteCode"] as? String ?? "",
            createdAt: (d["createdAt"] as? Timestamp)?.dateValue() ?? Date()
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
            date: (d["date"] as? Timestamp)?.dateValue() ?? Date(),
            notes: d["notes"] as? String ?? "",
            addedBy: d["addedBy"] as? String ?? "",
            addedByName: d["addedByName"] as? String ?? "",
            createdAt: (d["createdAt"] as? Timestamp)?.dateValue() ?? Date(),
            updatedAt: (d["updatedAt"] as? Timestamp)?.dateValue() ?? Date()
        )
    }

    private func encodeExpense(_ e: Expense) -> [String: Any] {
        ["householdId": e.householdId, "amount": e.amount, "categoryId": e.categoryId,
         "categoryName": e.categoryName, "date": Timestamp(date: e.date), "notes": e.notes,
         "addedBy": e.addedBy, "addedByName": e.addedByName,
         "createdAt": Timestamp(date: e.createdAt), "updatedAt": Timestamp(date: e.updatedAt)]
    }

    private func decodeCategory(_ doc: DocumentSnapshot) -> Category? {
        guard let d = doc.data() else { return nil }
        return Category(id: doc.documentID, name: d["name"] as? String ?? "",
                        icon: d["icon"] as? String ?? "", color: d["color"] as? Int64 ?? 0,
                        isPreset: d["isPreset"] as? Bool ?? false, householdId: d["householdId"] as? String ?? "")
    }

    private func encodeCategory(_ c: Category) -> [String: Any] {
        ["name": c.name, "icon": c.icon, "color": c.color, "isPreset": c.isPreset, "householdId": c.householdId]
    }

    private func decodeAsset(_ doc: DocumentSnapshot) -> Asset? {
        guard let d = doc.data() else { return nil }
        return Asset(id: doc.documentID, householdId: d["householdId"] as? String ?? "",
                     name: d["name"] as? String ?? "", value: d["value"] as? Double ?? 0,
                     type: d["type"] as? String ?? "", date: (d["date"] as? Timestamp)?.dateValue() ?? Date(),
                     addedBy: d["addedBy"] as? String ?? "")
    }

    private func encodeAsset(_ a: Asset) -> [String: Any] {
        ["householdId": a.householdId, "name": a.name, "value": a.value, "type": a.type,
         "date": Timestamp(date: a.date), "addedBy": a.addedBy]
    }

    private func decodeLiability(_ doc: DocumentSnapshot) -> Liability? {
        guard let d = doc.data() else { return nil }
        return Liability(id: doc.documentID, householdId: d["householdId"] as? String ?? "",
                         name: d["name"] as? String ?? "", amount: d["amount"] as? Double ?? 0,
                         type: d["type"] as? String ?? "", date: (d["date"] as? Timestamp)?.dateValue() ?? Date(),
                         addedBy: d["addedBy"] as? String ?? "")
    }

    private func encodeLiability(_ l: Liability) -> [String: Any] {
        ["householdId": l.householdId, "name": l.name, "amount": l.amount, "type": l.type,
         "date": Timestamp(date: l.date), "addedBy": l.addedBy]
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
                           targetDate: (d["targetDate"] as? Timestamp)?.dateValue(),
                           createdAt: (d["createdAt"] as? Timestamp)?.dateValue() ?? Date())
    }

    private func encodeSavingsGoal(_ g: SavingsGoal) -> [String: Any] {
        var data: [String: Any] = ["householdId": g.householdId, "name": g.name, "targetAmount": g.targetAmount,
                                    "currentAmount": g.currentAmount, "icon": g.icon,
                                    "createdAt": Timestamp(date: g.createdAt)]
        if let td = g.targetDate { data["targetDate"] = Timestamp(date: td) }
        return data
    }
}
