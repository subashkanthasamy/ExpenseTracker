import SwiftUI

/// Shown while Firebase resolves whether there is a persisted session.
///
/// Without this the app rendered `LoginView` off the initial `isAuthenticated == false`,
/// so a signed-in user saw the sign-in screen flash before the app appeared. A neutral
/// branded screen is also better than the blank white frame that `ProgressView` produced.
struct LaunchSplashView: View {
    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()

            VStack(spacing: 20) {
                Image(systemName: "indianrupeesign.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(AppColors.gradient)

                Text("Expense Tracker")
                    .font(.title2).bold()

                ProgressView()
                    .tint(AppColors.accentPurple)
                    .padding(.top, 4)
            }
        }
    }
}
