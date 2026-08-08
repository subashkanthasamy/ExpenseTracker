import SwiftUI

/// Shown ahead of the app content when the biometric lock is enabled, mirroring Android's
/// biometric prompt in MainActivity.onCreate.
///
/// Deliberately does NOT auto-unlock on failure: Android falls through to the app on error,
/// which makes the lock decorative. Here a failed or cancelled check leaves the user on this
/// screen with a Retry, so the setting actually means something.
struct BiometricLockView: View {
    let onUnlock: () -> Void

    @State private var checking = true
    @State private var failed = false

    private let biometrics = BiometricService()

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "lock.shield.fill")
                .font(.system(size: 64))
                .foregroundStyle(AppColors.gradient)

            Text("Expense Tracker")
                .font(.title2).bold()

            if checking {
                ProgressView()
            } else if failed {
                Text("Authentication failed")
                    .foregroundStyle(DS.textSecondary)
                Button("Retry") { Task { await unlock() } }
                    .buttonStyle(.borderedProminent)
                    .tint(AppColors.accentPurple)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task { await unlock() }
    }

    private func unlock() async {
        checking = true
        failed = false
        let ok = await biometrics.authenticate()
        checking = false
        if ok { onUnlock() } else { failed = true }
    }
}
