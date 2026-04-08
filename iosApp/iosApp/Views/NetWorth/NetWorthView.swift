import SwiftUI

struct NetWorthView: View {
    @Bindable var viewModel: NetWorthViewModel
    @State private var showAddAsset = false
    @State private var showAddLiability = false
    @State private var newName = ""
    @State private var newAmount = ""
    @State private var newType = ""
    @State private var selectedTab = 0

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Summary
                VStack(spacing: 8) {
                    Text("Net Worth").font(.subheadline).foregroundStyle(.secondary)
                    Text(formatCurrency(viewModel.netWorth))
                        .font(.system(size: 32, weight: .bold))
                        .foregroundStyle(viewModel.netWorth >= 0 ? AppColors.incomeGreen : AppColors.expenseRed)
                    HStack(spacing: 24) {
                        VStack {
                            Text("Assets").font(.caption).foregroundStyle(.secondary)
                            Text(formatAmount(viewModel.totalAssets)).font(.subheadline).bold()
                                .foregroundStyle(AppColors.incomeGreen)
                        }
                        VStack {
                            Text("Liabilities").font(.caption).foregroundStyle(.secondary)
                            Text(formatAmount(viewModel.totalLiabilities)).font(.subheadline).bold()
                                .foregroundStyle(AppColors.expenseRed)
                        }
                    }
                }
                .frame(maxWidth: .infinity).padding()
                .background(.regularMaterial).clipShape(RoundedRectangle(cornerRadius: 16))

                Picker("", selection: $selectedTab) {
                    Text("Assets").tag(0)
                    Text("Liabilities").tag(1)
                }.pickerStyle(.segmented)

                if selectedTab == 0 {
                    ForEach(viewModel.assets) { asset in
                        HStack {
                            VStack(alignment: .leading) { Text(asset.name).bold(); Text(asset.type).font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            Text(formatCurrency(asset.value)).bold().foregroundStyle(AppColors.incomeGreen)
                        }
                        .padding().background(.regularMaterial).clipShape(RoundedRectangle(cornerRadius: 12))
                        .swipeActions { Button(role: .destructive) { Task { await viewModel.deleteAsset(asset.id) } } label: { Label("Delete", systemImage: "trash") } }
                    }
                } else {
                    ForEach(viewModel.liabilities) { l in
                        HStack {
                            VStack(alignment: .leading) { Text(l.name).bold(); Text(l.type).font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            Text(formatCurrency(l.amount)).bold().foregroundStyle(AppColors.expenseRed)
                        }
                        .padding().background(.regularMaterial).clipShape(RoundedRectangle(cornerRadius: 12))
                        .swipeActions { Button(role: .destructive) { Task { await viewModel.deleteLiability(l.id) } } label: { Label("Delete", systemImage: "trash") } }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Net Worth")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Add Asset") { showAddAsset = true }
                    Button("Add Liability") { showAddLiability = true }
                } label: { Image(systemName: "plus") }
            }
        }
        .alert("Add Asset", isPresented: $showAddAsset) {
            TextField("Name", text: $newName)
            TextField("Value", text: $newAmount)
            TextField("Type (e.g. Property, Stocks)", text: $newType)
            Button("Add") { if let v = Double(newAmount) { Task { await viewModel.addAsset(name: newName, value: v, type: newType) }; clearFields() } }
            Button("Cancel", role: .cancel) { clearFields() }
        }
        .alert("Add Liability", isPresented: $showAddLiability) {
            TextField("Name", text: $newName)
            TextField("Amount", text: $newAmount)
            TextField("Type (e.g. Loan, Credit Card)", text: $newType)
            Button("Add") { if let v = Double(newAmount) { Task { await viewModel.addLiability(name: newName, amount: v, type: newType) }; clearFields() } }
            Button("Cancel", role: .cancel) { clearFields() }
        }
        .task { await viewModel.load() }
    }

    private func clearFields() { newName = ""; newAmount = ""; newType = "" }
}
