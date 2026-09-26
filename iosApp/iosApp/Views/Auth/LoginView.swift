import SwiftUI
import Shared

struct LoginView: View {
    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false
    @Bindable var viewModel: AuthViewModel
    var onSignUp: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "indianrupeesign.circle.fill")
                .font(.system(size: 80))
                .foregroundStyle(AppColors.gradient)

            Text("Expense Tracker")
                .font(.largeTitle).bold()

            Text("Track. Save. Grow.")
                .foregroundStyle(DS.textSecondary)

            VStack(spacing: 16) {
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)

                HStack {
                    if showPassword {
                        TextField("Password", text: $password)
                    } else {
                        SecureField("Password", text: $password)
                    }
                    Button { showPassword.toggle() } label: {
                        Image(systemName: showPassword ? "eye.slash" : "eye")
                            .foregroundStyle(DS.textSecondary)
                    }
                }
                .textFieldStyle(.roundedBorder)
            }
            .padding(.horizontal)

            if let error = viewModel.error {
                Text(error)
                    .foregroundStyle(.red)
                    .font(.caption)
                    .padding(.horizontal)
                    .multilineTextAlignment(.center)
            }

            Button {
                guard !email.isEmpty, !password.isEmpty else {
                    viewModel.error = "Enter your email and password."
                    return
                }
                Task {
                    await viewModel.signIn(email: email, password: password)
                }
            } label: {
                if viewModel.isLoading {
                    ProgressView().tint(.white)
                } else {
                    Text("Sign in")
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(AppColors.accentPurple)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal)
            .disabled(viewModel.isLoading || email.isEmpty || password.isEmpty)

            Button("Forgot password?") {
                Task { await viewModel.sendPasswordReset(email: email) }
            }
            .font(.subheadline)
            .foregroundStyle(AppColors.accentPurple)
            .disabled(viewModel.isLoading)

            Button {
                Task { await viewModel.signInWithGoogle() }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "g.circle.fill")
                    Text("Sign in with Google")
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .foregroundStyle(AppColors.accentPurple)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(AppColors.accentPurple.opacity(0.5), lineWidth: 1)
            )
            .padding(.horizontal)
            .disabled(viewModel.isLoading)

            Button("Don't have an account? Sign up", action: onSignUp)
                .foregroundStyle(AppColors.accentPurple)

            Spacer()
        }
        // "If an account exists" is load-bearing rather than hedging: the send reports success
        // even for an unregistered address, so that this screen cannot be used to find out who
        // has an account. See AuthService.sendPasswordReset.
        .alert(
            "Check your email",
            isPresented: Binding(
                get: { viewModel.passwordResetSentTo != nil },
                set: { if !$0 { viewModel.passwordResetSentTo = nil } }
            )
        ) {
            Button("Done", role: .cancel) { viewModel.passwordResetSentTo = nil }
        } message: {
            Text(
                "If an account exists for \(viewModel.passwordResetSentTo ?? ""), a password "
                    + "reset link is on its way. The link expires after an hour."
            )
        }
    }
}
