import SwiftUI

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

            Text("Set Up Household")
                .font(.title).bold()

            Text(isJoining ? "Enter the invite code to join" : "Create a new household to get started")
                .foregroundStyle(.secondary).multilineTextAlignment(.center)

            if isJoining {
                TextField("Invite Code", text: $inviteCode)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.allCharacters)
                    .padding(.horizontal)

                Button {
                    Task { await viewModel.joinHousehold(inviteCode: inviteCode) }
                } label: {
                    if viewModel.isLoading { ProgressView().tint(.white) }
                    else { Text("Join Household") }
                }
                .frame(maxWidth: .infinity).padding()
                .background(AppColors.accentPurple).foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal)
                .disabled(inviteCode.count < 6 || viewModel.isLoading)
            } else {
                TextField("Household Name", text: $householdName)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                Button {
                    Task { await viewModel.createHousehold(name: householdName) }
                } label: {
                    if viewModel.isLoading { ProgressView().tint(.white) }
                    else { Text("Create Household") }
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

            Button(isJoining ? "Create new instead" : "Join existing household") {
                isJoining.toggle()
            }
            .foregroundStyle(AppColors.accentPurple)

            Spacer()
        }
    }
}
