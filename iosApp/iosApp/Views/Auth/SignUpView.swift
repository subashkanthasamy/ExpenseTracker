import SwiftUI

struct SignUpView: View {
    @State private var displayName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @Bindable var viewModel: AuthViewModel
    var onBack: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                }
                Spacer()
                Text("Create Account").font(.title2).bold()
                Spacer()
            }
            .padding(.horizontal)

            VStack(spacing: 16) {
                TextField("Display Name", text: $displayName)
                    .textFieldStyle(.roundedBorder)
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .textFieldStyle(.roundedBorder)
                SecureField("Password", text: $password)
                    .textFieldStyle(.roundedBorder)
                SecureField("Confirm Password", text: $confirmPassword)
                    .textFieldStyle(.roundedBorder)
            }
            .padding(.horizontal)

            if password != confirmPassword && !confirmPassword.isEmpty {
                Text("Passwords don't match")
                    .foregroundStyle(.red).font(.caption)
            }

            if let error = viewModel.error {
                Text(error).foregroundStyle(.red).font(.caption)
            }

            Button {
                Task { await viewModel.signUp(email: email, password: password, displayName: displayName) }
            } label: {
                if viewModel.isLoading { ProgressView().tint(.white) }
                else { Text("Sign Up") }
            }
            .frame(maxWidth: .infinity).padding()
            .background(AppColors.accentPurple).foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal)
            .disabled(password != confirmPassword || password.isEmpty || displayName.isEmpty || viewModel.isLoading)

            Spacer()
        }
        .padding(.top)
    }
}
