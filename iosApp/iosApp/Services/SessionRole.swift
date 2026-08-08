import Foundation
import Shared

/// The signed-in user's role in their active household, shared across screens.
///
/// Created once in `ContentView` and passed down, so Budgets, Categories, Savings, Recurring
/// and Net Worth do not each load the household and the ID token just to decide whether to
/// draw an add button.
///
/// Advisory only. `firestore.rules` is the enforcement — this decides which controls render.
/// Failing to resolve leaves the role at `.none`, which hides everything destructive; that is
/// the safe direction to fail in.
@Observable
@MainActor
class SessionRole {
    private(set) var role: HouseholdRole = HouseholdRole.none
    private(set) var uid: String = ""

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private var loadedHouseholdId: String?

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    var isOwner: Bool { Permissions.shared.canManageMembers(role: role) }
    var canManageSharedConfig: Bool { Permissions.shared.canManageSharedConfig(role: role) }
    var canAddExpense: Bool { Permissions.shared.canAddExpense(role: role) }

    func canEdit(_ expense: Expense) -> Bool {
        Permissions.shared.canEditExpense(role: role, expense: expense, uid: uid)
    }

    /// Resolves the role for the active household. Cheap to call repeatedly — it only refetches
    /// when the active household changes, or after `invalidate()`.
    func refresh() async {
        guard let currentUid = authService.currentUserId else {
            role = HouseholdRole.none
            uid = ""
            return
        }
        uid = currentUid

        guard let hid = await authService.getActiveHouseholdId() else {
            role = HouseholdRole.none
            return
        }
        if loadedHouseholdId == hid { return }

        guard let household = try? await firestoreService.getHousehold(hid) else {
            role = HouseholdRole.none
            return
        }
        let admin = await authService.isAdmin()
        role = Permissions.shared.roleOf(household: household, uid: currentUid, isAdmin: admin)
        loadedHouseholdId = hid
    }

    /// Drop the cache — after switching household, joining, leaving or a role change.
    func invalidate() {
        loadedHouseholdId = nil
    }
}
