import SwiftUI

struct HouseholdView: View {
    @Bindable var viewModel: HouseholdViewModel
    @State private var showDelete = false

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Loading household...")
            } else if let h = viewModel.household {
                List {
                    Section("Active Household") {
                        LabeledContent("Name", value: h.name)
                        LabeledContent("Invite Code") {
                            HStack {
                                Text(h.inviteCode).font(.system(.body, design: .monospaced)).bold()
                                Button {
                                    UIPasteboard.general.string = h.inviteCode
                                } label: {
                                    Image(systemName: "doc.on.doc").font(.caption)
                                }
                            }
                        }
                        LabeledContent("Members", value: "\(h.memberUids.count)")
                        LabeledContent("Created", value: h.createdAt.formatted(date: .abbreviated, time: .omitted))
                    }

                    if viewModel.households.count > 1 {
                        Section("All Households") {
                            ForEach(viewModel.households) { household in
                                HStack {
                                    Text(household.name)
                                    Spacer()
                                    if household.id == h.id {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(AppColors.accentPurple)
                                    }
                                }
                            }
                        }
                    }

                    Section {
                        Button("Delete Household", role: .destructive) { showDelete = true }
                    }
                }
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "house.slash").font(.system(size: 48)).foregroundStyle(.secondary)
                    Text("No household found").foregroundStyle(.secondary)
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
