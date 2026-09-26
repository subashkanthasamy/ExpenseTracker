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
            error = "You're not in a household yet. Create or join one first."
            return
        }
        isBusy = true
        error = nil
        do {
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            guard !expenses.isEmpty else {
                error = "There are no expenses to export yet."
                isBusy = false
                return
            }
            exportedFile = try exportService.export(expenses, as: format)
        } catch {
            self.error = "Couldn't export your expenses. Try again."
        }
        isBusy = false
    }

    // MARK: - Import

    func importCSV(from url: URL) async {
        guard let hid = await authService.getActiveHouseholdId() else {
            error = "You're not in a household yet. Create or join one first."
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
                ? "Imported \(result.imported) \(result.imported == 1 ? "expense" : "expenses")"
                : "Imported \(result.imported) \(result.imported == 1 ? "expense" : "expenses"). Skipped \(result.skipped) \(result.skipped == 1 ? "row" : "rows") that couldn't be read."
        } catch let importError as ExportService.ImportError {
            self.error = importError.errorDescription
        } catch {
            self.error = "Couldn't import the file. Check that it's a CSV file and try again."
        }
        isBusy = false
    }

    // MARK: - Destructive

    func resetAllExpenses() async {
        guard let hid = await authService.getActiveHouseholdId() else {
            error = "You're not in a household yet. Create or join one first."
            return
        }
        isBusy = true
        error = nil
        do {
            let expenses = try await firestoreService.getExpenses(householdId: hid)
            for expense in expenses {
                try await firestoreService.deleteExpense(householdId: hid, expenseId: expense.id)
            }
            statusMessage = "Deleted \(expenses.count) \(expenses.count == 1 ? "expense" : "expenses")"
        } catch {
            self.error = "Couldn't delete all expenses. Check your connection and try again."
        }
        isBusy = false
    }

    /// Require a successful biometric check before turning the lock on, so a user can't
    /// enable it and then be unable to get back in.
    func setBiometricEnabled(_ enabled: Bool, prefs: AppPreferences) async {
        if enabled {
            guard biometricAvailable else {
                error = "Face ID and Touch ID aren't available on this device."
                return
            }
            if await biometricService.authenticate() {
                prefs.biometricEnabled = true
            } else {
                error = "Couldn't verify it's you, so the lock is still off."
                prefs.biometricEnabled = false
            }
        } else {
            prefs.biometricEnabled = false
        }
    }
}
