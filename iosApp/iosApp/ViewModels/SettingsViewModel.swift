import Foundation
import Shared

@MainActor
@Observable
class SettingsViewModel {
    var isBusy = false
    var error: String?
    var statusMessage: String?
    var exportedFile: URL?

    let biometricAvailable: Bool

    private let authService: AuthService
    private let firestoreService: FirestoreService
    private let exportService = ExportService()
    private let biometricService = BiometricService()

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
        self.biometricAvailable = BiometricService().canAuthenticate()
    }

    // MARK: - Export

    func export(as format: ExportService.Format) async {
        guard let hid = await authService.getActiveHouseholdId() else {
            error = "No active household"
            return
        }
        isBusy = true
        error = nil
        do {
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            guard !expenses.isEmpty else {
                error = "There are no expenses to export"
                isBusy = false
                return
            }
            exportedFile = try exportService.export(expenses, as: format)
        } catch {
            self.error = error.localizedDescription
        }
        isBusy = false
    }

    // MARK: - Import

    func importCSV(from url: URL) async {
        guard let hid = await authService.getActiveHouseholdId() else {
            error = "No active household"
            return
        }
        isBusy = true
        error = nil
        do {
            let (expenses, result) = try exportService.parseCSV(
                at: url,
                householdId: hid,
                addedBy: authService.currentUserId ?? "",
                addedByName: authService.currentUserDisplayName ?? "Me"
            )
            for expense in expenses {
                try await firestoreService.addExpense(householdId: hid, expense: expense)
            }
            statusMessage = result.skipped == 0
                ? "Imported \(result.imported) expenses"
                : "Imported \(result.imported) expenses, skipped \(result.skipped) unreadable rows"
        } catch {
            self.error = error.localizedDescription
        }
        isBusy = false
    }

    // MARK: - Destructive

    func resetAllExpenses() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            error = "No active household"
            return
        }
        isBusy = true
        error = nil
        do {
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            for expense in expenses {
                try await firestoreService.deleteExpense(householdId: hid, expenseId: expense.id)
            }
            statusMessage = "Deleted \(expenses.count) expenses"
        } catch {
            self.error = error.localizedDescription
        }
        isBusy = false
    }

    /// Require a successful biometric check before turning the lock on, so a user can't
    /// enable it and then be unable to get back in.
    func setBiometricEnabled(_ enabled: Bool, prefs: AppPreferences) async {
        if enabled {
            guard biometricAvailable else {
                error = "Biometrics are not available on this device"
                return
            }
            if await biometricService.authenticate() {
                prefs.biometricEnabled = true
            } else {
                error = "Could not verify biometrics — lock not enabled"
                prefs.biometricEnabled = false
            }
        } else {
            prefs.biometricEnabled = false
        }
    }
}
