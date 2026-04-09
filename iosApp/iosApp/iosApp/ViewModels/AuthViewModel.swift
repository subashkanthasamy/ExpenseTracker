import Foundation

@MainActor
@Observable
class AuthViewModel {
    var isLoading = false
    var error: String?
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
            self.error = err.localizedDescription
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
            self.error = err.localizedDescription
            isLoading = false
        }
    }

    func createHousehold(name: String) async {
        guard let uid = authService.currentUserId else { return }
        isLoading = true
        do {
            let household = Household(
                id: UUID().uuidString, name: name, memberUids: [uid],
                inviteCode: String((0..<6).map { _ in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".randomElement()! }),
                createdAt: Date()
            )
            try await firestoreService.createHousehold(household)
            if var user = authService.currentUser {
                user.householdIds.append(household.id)
                user.activeHouseholdId = household.id
                try await authService.saveUser(user)
                authService.currentUser = user
            }
            needsHouseholdSetup = false
            isLoading = false
        } catch let err {
            print("Create household error: \(err)")
            self.error = err.localizedDescription
            isLoading = false
        }
    }

    func joinHousehold(inviteCode: String) async {
        guard let uid = authService.currentUserId else { return }
        isLoading = true
        do {
            guard let household = try await firestoreService.getHouseholdByInviteCode(inviteCode) else {
                self.error = "Invalid invite code"
                isLoading = false
                return
            }
            var members = household.memberUids
            if !members.contains(uid) { members.append(uid) }
            try await firestoreService.updateHouseholdMembers(household.id, members: members)
            if var user = authService.currentUser {
                user.householdIds.append(household.id)
                user.activeHouseholdId = household.id
                try await authService.saveUser(user)
                authService.currentUser = user
            }
            needsHouseholdSetup = false
            isLoading = false
        } catch let err {
            print("Join household error: \(err)")
            self.error = err.localizedDescription
            isLoading = false
        }
    }

    func signOut() {
        authService.signOut()
        isAuthenticated = false
    }
}
