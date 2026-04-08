import SwiftUI

struct HouseholdView: View {
    @Bindable var viewModel: HouseholdViewModel
    @State private var showDelete = false

    var body: some View {
        List {
            if let h = viewModel.household {
                Section("Active Household") {
                    LabeledContent("Name", value: h.name)
                    LabeledContent("Invite Code") {
                        HStack {
                            Text(h.inviteCode).font(.system(.body, design: .monospaced)).bold()
                            Button { UIPasteboard.general.string = h.inviteCode } label: {
                                Image(systemName: "doc.on.doc").font(.caption)
                            }
                        }
                    }
                    LabeledContent("Members", value: "\(h.memberUids.count)")
                }

                Section {
                    Button("Delete Household", role: .destructive) { showDelete = true }
                }
            }

            if viewModel.households.count > 1 {
                Section("All Households") {
                    ForEach(viewModel.households) { h in
                        HStack {
                            Text(h.name)
                            Spacer()
                            if h.id == viewModel.household?.id {
                                Image(systemName: "checkmark").foregroundStyle(AppColors.accentPurple)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Household")
        .alert("Delete Household?", isPresented: $showDelete) {
            Button("Delete", role: .destructive) { Task { await viewModel.deleteHousehold() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will permanently delete the household and all its data.")
        }
        .task { await viewModel.load() }
    }
}
