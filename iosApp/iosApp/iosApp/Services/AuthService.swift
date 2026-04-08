import Foundation
import FirebaseAuth
import FirebaseFirestore

@Observable
class AuthService {
    var currentUser: AppUser?
    var isAuthenticated = false

    private let auth = Auth.auth()
    private let db = Firestore.firestore()

    init() {
        auth.addStateDidChangeListener { [weak self] _, firebaseUser in
            Task { await self?.handleAuthStateChange(firebaseUser) }
        }
    }

    @MainActor
    private func handleAuthStateChange(_ firebaseUser: FirebaseAuth.User?) async {
        guard let fu = firebaseUser else {
            currentUser = nil
            isAuthenticated = false
            return
        }
        if let user = try? await fetchUser(uid: fu.uid) {
            currentUser = user
        } else {
            currentUser = AppUser(uid: fu.uid, email: fu.email ?? "", displayName: fu.displayName ?? "User")
        }
        isAuthenticated = true
    }

    func signInWithEmail(_ email: String, password: String) async throws -> AppUser {
        let result = try await auth.signIn(withEmail: email, password: password)
        let user = try await fetchOrCreateUser(result.user)
        await MainActor.run { currentUser = user; isAuthenticated = true }
        return user
    }

    func signUpWithEmail(_ email: String, password: String, displayName: String) async throws -> AppUser {
        let result = try await auth.createUser(withEmail: email, password: password)
        let changeRequest = result.user.createProfileChangeRequest()
        changeRequest.displayName = displayName
        try await changeRequest.commitChanges()

        let user = AppUser(uid: result.user.uid, email: email, displayName: displayName)
        try await saveUser(user)
        await MainActor.run { currentUser = user; isAuthenticated = true }
        return user
    }

    func signOut() throws {
        try auth.signOut()
        currentUser = nil
        isAuthenticated = false
    }

    var currentUserId: String? { auth.currentUser?.uid }
    var currentUserDisplayName: String? { auth.currentUser?.displayName }

    private func fetchUser(uid: String) async throws -> AppUser? {
        let doc = try await db.collection("users").document(uid).getDocument()
        guard doc.exists, let data = doc.data() else { return nil }
        return AppUser(
            uid: uid,
            email: data["email"] as? String ?? "",
            displayName: data["displayName"] as? String ?? "",
            householdIds: data["householdIds"] as? [String] ?? [],
            activeHouseholdId: data["activeHouseholdId"] as? String
        )
    }

    private func fetchOrCreateUser(_ firebaseUser: FirebaseAuth.User) async throws -> AppUser {
        if let existing = try await fetchUser(uid: firebaseUser.uid) { return existing }
        let user = AppUser(uid: firebaseUser.uid, email: firebaseUser.email ?? "", displayName: firebaseUser.displayName ?? "User")
        try await saveUser(user)
        return user
    }

    func saveUser(_ user: AppUser) async throws {
        try await db.collection("users").document(user.uid).setData([
            "email": user.email,
            "displayName": user.displayName,
            "householdIds": user.householdIds,
            "activeHouseholdId": user.activeHouseholdId as Any
        ], merge: true)
    }
}
