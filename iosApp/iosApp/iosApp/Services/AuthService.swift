import Foundation
import FirebaseAuth
import FirebaseFirestore

@MainActor
@Observable
class AuthService {
    var currentUser: AppUser?
    var isAuthenticated = false

    private nonisolated let auth = Auth.auth()
    private nonisolated let db = Firestore.firestore()

    init() {
        let authRef = auth
        authRef.addStateDidChangeListener { [weak self] _, firebaseUser in
            Task { @MainActor in
                await self?.handleAuthStateChange(firebaseUser)
            }
        }
    }

    private func handleAuthStateChange(_ firebaseUser: FirebaseAuth.User?) async {
        guard let fu = firebaseUser else {
            currentUser = nil
            isAuthenticated = false
            return
        }
        do {
            if let user = try await fetchUser(uid: fu.uid) {
                // Only update if we don't already have a more up-to-date local copy
                if currentUser == nil || currentUser?.uid != fu.uid {
                    currentUser = user
                }
                isAuthenticated = true
            } else {
                if currentUser == nil {
                    currentUser = AppUser(uid: fu.uid, email: fu.email ?? "", displayName: fu.displayName ?? "User")
                }
                isAuthenticated = true
            }
        } catch {
            print("Auth state change error: \(error)")
            if currentUser == nil {
                currentUser = AppUser(uid: fu.uid, email: fu.email ?? "", displayName: fu.displayName ?? "User")
            }
            isAuthenticated = true
        }
    }

    func signInWithEmail(_ email: String, password: String) async throws -> AppUser {
        let result = try await auth.signIn(withEmail: email, password: password)
        let user = try await fetchOrCreateUser(result.user)
        currentUser = user
        isAuthenticated = true
        print("SignIn success: user=\(user.uid), activeHousehold=\(user.activeHouseholdId ?? "nil")")
        return user
    }

    func signUpWithEmail(_ email: String, password: String, displayName: String) async throws -> AppUser {
        let result = try await auth.createUser(withEmail: email, password: password)
        let changeRequest = result.user.createProfileChangeRequest()
        changeRequest.displayName = displayName
        try await changeRequest.commitChanges()

        let user = AppUser(uid: result.user.uid, email: email, displayName: displayName)
        try await saveUser(user)
        currentUser = user
        isAuthenticated = true
        return user
    }

    func signOut() {
        try? auth.signOut()
        currentUser = nil
        isAuthenticated = false
    }

    nonisolated var currentUserId: String? { auth.currentUser?.uid }
    nonisolated var currentUserDisplayName: String? { auth.currentUser?.displayName }

    /// Reliably get the active household ID - checks local user, then fetches from Firestore
    func getActiveHouseholdId() async -> String? {
        if let hid = currentUser?.activeHouseholdId { return hid }
        // Fallback: fetch user's households and use first one
        guard let uid = currentUserId else { return nil }
        let db = self.db
        do {
            let snap = try await db.collection("households").whereField("memberUids", arrayContains: uid).limit(to: 1).getDocuments()
            if let doc = snap.documents.first {
                let hid = doc.documentID
                // Update local user
                currentUser?.activeHouseholdId = hid
                currentUser?.householdIds = [hid]
                return hid
            }
        } catch {
            print("getActiveHouseholdId error: \(error)")
        }
        return nil
    }

    private nonisolated func fetchUser(uid: String) async throws -> AppUser? {
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

    private nonisolated func fetchOrCreateUser(_ firebaseUser: FirebaseAuth.User) async throws -> AppUser {
        if let existing = try await fetchUser(uid: firebaseUser.uid) { return existing }
        let user = AppUser(uid: firebaseUser.uid, email: firebaseUser.email ?? "", displayName: firebaseUser.displayName ?? "User")
        try await saveUser(user)
        return user
    }

    nonisolated func saveUser(_ user: AppUser) async throws {
        try await db.collection("users").document(user.uid).setData([
            "email": user.email,
            "displayName": user.displayName,
            "householdIds": user.householdIds,
            "activeHouseholdId": user.activeHouseholdId as Any
        ], merge: true)
    }
}
