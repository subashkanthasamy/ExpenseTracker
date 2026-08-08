import Foundation
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import GoogleSignIn
import UIKit
import Shared

enum GoogleSignInError: LocalizedError {
    case missingClientID
    case noPresenter
    case missingIDToken
    /// User dismissed the Google sheet — callers should stay silent rather than show an error.
    case cancelled

    var errorDescription: String? {
        switch self {
        case .missingClientID:
            return "Google Sign-In is not configured (no CLIENT_ID in GoogleService-Info.plist)."
        case .noPresenter:
            return "Could not find a window to present Google Sign-In."
        case .missingIDToken:
            return "Google did not return an ID token."
        case .cancelled:
            return "Google Sign-In was cancelled."
        }
    }
}

@MainActor
@Observable
class AuthService {
    var currentUser: AppUser?
    var isAuthenticated = false
    /// False until Firebase's first auth-state callback arrives.
    ///
    /// Firebase restores a persisted session asynchronously, so `isAuthenticated` is
    /// momentarily false at launch even for a signed-in user. Rendering the sign-in screen
    /// off that initial false is what made Login flash before the app appeared.
    var hasResolvedInitialState = false

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
        defer { hasResolvedInitialState = true }
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

    /// Mirrors the Android flow: get a Google ID token, exchange it for a Firebase
    /// credential, then reuse the same fetch-or-create path as email sign-in.
    func signInWithGoogle() async throws -> AppUser {
        // CLIENT_ID comes from GoogleService-Info.plist via FirebaseApp — never hardcode it.
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            throw GoogleSignInError.missingClientID
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let presenter = Self.topViewController() else {
            throw GoogleSignInError.noPresenter
        }

        let gidResult: GIDSignInResult
        do {
            gidResult = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        } catch let err as NSError where err.domain == kGIDSignInErrorDomain
            && err.code == GIDSignInError.canceled.rawValue {
            // Translate here so callers don't need to import GoogleSignIn.
            throw GoogleSignInError.cancelled
        }

        guard let idToken = gidResult.user.idToken?.tokenString else {
            throw GoogleSignInError.missingIDToken
        }

        let credential = GoogleAuthProvider.credential(
            withIDToken: idToken,
            accessToken: gidResult.user.accessToken.tokenString
        )
        let result = try await auth.signIn(with: credential)
        let user = try await fetchOrCreateUser(result.user)
        currentUser = user
        isAuthenticated = true
        return user
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
            ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }

    func signOut() {
        try? auth.signOut()
        GIDSignIn.sharedInstance.signOut()
        currentUser = nil
        isAuthenticated = false
    }

    nonisolated var currentUserId: String? { auth.currentUser?.uid }

    /// Whether this account carries the global `admin` custom claim.
    ///
    /// Read from the ID token, never from a document the account could write — a role field on
    /// `users/{uid}` would be self-grantable, since the rules let a user write their own
    /// profile. Claims are set server-side with the Admin SDK.
    ///
    /// Advisory only: this decides which buttons appear. `firestore.rules` re-checks the same
    /// claim and is what actually stops an admin action.
    func isAdmin() async -> Bool {
        guard let user = auth.currentUser else { return false }
        // forcingRefresh: false — a refresh here would add a round trip to every household
        // load; a freshly granted claim lands within the hour, or on next sign-in.
        guard let result = try? await user.getIDTokenResult(forcingRefresh: false) else {
            return false
        }
        return result.claims["admin"] as? Bool ?? false
    }
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
                // Update local user (shared User is immutable — rebuild it)
                currentUser = currentUser?.with(householdIds: [hid], activeHouseholdId: .some(hid))
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
