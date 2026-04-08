import LocalAuthentication

class BiometricService {
    func canAuthenticate() -> Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    func authenticate() async -> Bool {
        let context = LAContext()
        context.localizedReason = "Unlock Expense Tracker"
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Authenticate to access your expenses")
        } catch {
            return false
        }
    }
}
