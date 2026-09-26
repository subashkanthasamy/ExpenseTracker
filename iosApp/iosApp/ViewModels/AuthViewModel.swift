import Foundation
import Shared

@MainActor
@Observable
class AuthViewModel {
    var isLoading = false
    var error: String?
    /// Address a reset link was just sent to. Set even when no account existed for it, so the
    /// confirmation wording must stay conditional — see AuthService.sendPasswordReset.
    var passwordResetSentTo: String?
    var isAuthenticated = false
    var needsHouseholdSetup = false

    private let authService: AuthService
    private let firestoreService: FirestoreService

    init(authService: AuthService, firestoreService: FirestoreService) {
        self.authService = authService
        self.firestoreService = firestoreService
    }

    func signIn(email: String, password: String) async {
        isLoading = true
        error = nil
        do {
            let user = try await authService.signInWithEmail(email, password: password)
            let households = try await firestoreService.getUserHouseholds(userId: user.uid)
            isAuthenticated = true
            needsHouseholdSetup = households.isEmpty
            isLoading = false
        } catch let err {
            print("Sign in error: \(err)")
            self.error = "Couldn't sign in. \(err.localizedDescription)"
            isLoading = false
        }
    }

    func sendPasswordReset(email: String) async {
        let address = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !address.isEmpty else {
            error = "Enter your email address first."
            return
        }
        isLoading = true
        error = nil
        passwordResetSentTo = nil
        do {
            try await authService.sendPasswordReset(address)
            passwordResetSentTo = address
        } catch {
            self.error = "Couldn't send the reset link. \(error.localizedDescription)"
        }
        isLoading = false
    }

    func signInWithGoogle() async {
        isLoading = true
        error = nil
        do {
            let user = try await authService.signInWithGoogle()
            let households = try await firestoreService.getUserHouseholds(userId: user.uid)
            isAuthenticated = true
            needsHouseholdSetup = households.isEmpty
            isLoading = false
        } catch GoogleSignInError.cancelled {
            // User dismissed the Google sheet — not worth surfacing as an error.
            isLoading = false
        } catch let err {
            print("Google sign in error: \(err)")
            self.error = "Couldn't sign in with Google. \(err.localizedDescription)"
            isLoading = false
        }
    }

    func signUp(email: String, password: String, displayName: String) async {
        isLoading = true
        error = nil
        do {
            _ = try await authService.signUpWithEmail(email, password: password, displayName: displayName)
            isAuthenticated = true
            needsHouseholdSetup = true
            isLoading = false
        } catch let err {
            print("Sign up error: \(err)")
            self.error = "Couldn't create your account. \(err.localizedDescription)"
            isLoading = false
        }
    }

    func createHousehold(name: String) async {
        guard let uid = authService.currentUserId else { return }
        isLoading = true
        do {
            let household = Household(
                id: UUID().uuidString, name: name, memberUids: [uid],
                // The creator is the owner, and starts as the only member with no roles
                // granted — the security rules reject a create that says otherwise.
                ownerUid: uid, roles: [:],
                inviteCode: String((0..<6).map { _ in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".randomElement()! }),
                createdAt: Date()
            )
            try await firestoreService.createHousehold(household)
            if let current = authService.currentUser {
                let user = current.addingHousehold(household.id)
                try await authService.saveUser(user)
                authService.currentUser = user
            }
            needsHouseholdSetup = false
            isLoading = false
        } catch let err {
            print("Create household error: \(err)")
            self.error = "Couldn't create the household. Check your connection and try again."
            isLoading = false
        }
    }

    func joinHousehold(inviteCode: String) async {
        guard let uid = authService.currentUserId else { return }
        isLoading = true
        do {
            guard let target = try await firestoreService.resolveInviteCode(inviteCode) else {
                self.error = "That invite code doesn't match a household. Check it and try again."
                isLoading = false
                return
            }
            // arrayUnion — no read of the household required, which is what the security
            // rules now expect from a non-member.
            try await firestoreService.addSelfToHousehold(target.householdId, uid: uid)
            if let current = authService.currentUser {
                let user = current.addingHousehold(target.householdId)
                try await authService.saveUser(user)
                authService.currentUser = user
            }
            needsHouseholdSetup = false
            isLoading = false
        } catch let err {
            print("Join household error: \(err)")
            self.error = "Couldn't join the household. Check your connection and try again."
            isLoading = false
        }
    }

    func signOut() {
        authService.signOut()
        isAuthenticated = false
    }
}
