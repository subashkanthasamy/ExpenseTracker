import SwiftUI
import Shared

struct HouseholdSetupView: View {
    @State private var householdName = ""
    @State private var inviteCode = ""
    @State private var isJoining = false
    @Bindable var viewModel: AuthViewModel

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "house.fill")
                .font(.system(size: 60))
                .foregroundStyle(AppColors.accentPurple)

            Text("Set up your household")
                .font(.title).bold()

            Text(isJoining ? "Enter an invite code to join a household." : "Create a household to get started.")
                .foregroundStyle(DS.textSecondary).multilineTextAlignment(.center)

            if isJoining {
                TextField("Invite code", text: $inviteCode)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.allCharacters)
                    .padding(.horizontal)

                Button {
                    Task { await viewModel.joinHousehold(inviteCode: inviteCode) }
                } label: {
                    if viewModel.isLoading { ProgressView().tint(.white) }
                    else { Text("Join household") }
                }
                .frame(maxWidth: .infinity).padding()
                .background(AppColors.accentPurple).foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal)
                .disabled(inviteCode.count < 6 || viewModel.isLoading)
            } else {
                TextField("Household name", text: $householdName)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                Button {
                    Task { await viewModel.createHousehold(name: householdName) }
                } label: {
                    if viewModel.isLoading { ProgressView().tint(.white) }
                    else { Text("Create household") }
                }
                .frame(maxWidth: .infinity).padding()
                .background(AppColors.accentPurple).foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal)
                .disabled(householdName.isEmpty || viewModel.isLoading)
            }

            if let error = viewModel.error {
                Text(error).foregroundStyle(.red).font(.caption)
            }

            Button(isJoining ? "Create a household instead" : "Join with an invite code") {
                isJoining.toggle()
            }
            .foregroundStyle(AppColors.accentPurple)

            Spacer()
        }
    }
}
