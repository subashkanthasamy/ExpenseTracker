import LocalAuthentication
import Shared

class BiometricService {
    func canAuthenticate() -> Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
    }

    func authenticate() async -> Bool {
        let context = LAContext()
        context.localizedReason = "Unlock Expense Tracker"
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Unlock to see your expenses")
        } catch {
            return false
        }
    }
}
