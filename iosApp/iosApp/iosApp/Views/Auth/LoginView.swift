import SwiftUI

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
                .foregroundStyle(.secondary)

            VStack(spacing: 16) {
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .textFieldStyle(.roundedBorder)

                HStack {
                    if showPassword {
                        TextField("Password", text: $password)
                    } else {
                        SecureField("Password", text: $password)
                    }
                    Button { showPassword.toggle() } label: {
                        Image(systemName: showPassword ? "eye.slash" : "eye")
                            .foregroundStyle(.secondary)
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
            }

            Button {
                Task { await viewModel.signIn(email: email, password: password) }
            } label: {
                if viewModel.isLoading {
                    ProgressView().tint(.white)
                } else {
                    Text("Sign In")
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(AppColors.accentPurple)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal)
            .disabled(viewModel.isLoading)

            Button("Don't have an account? Sign Up", action: onSignUp)
                .foregroundStyle(AppColors.accentPurple)

            Spacer()
        }
    }
}
