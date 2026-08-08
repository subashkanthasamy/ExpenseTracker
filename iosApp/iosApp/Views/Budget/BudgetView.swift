import SwiftUI
import Shared

struct BudgetView: View {
    @Bindable var viewModel: BudgetViewModel
    @State private var showAdd = false
    @State private var selectedCatId = ""
    @State private var limitStr = ""

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView()
            } else if viewModel.budgets.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "chart.pie").font(.system(size: 48)).foregroundStyle(DS.textSecondary)
                    Text("No budgets set").foregroundStyle(DS.textSecondary)
                    Text("Tap + to add a monthly budget").font(.caption).foregroundStyle(DS.textSecondary)
                }
            } else {
                List {
                    ForEach(viewModel.budgets) { budget in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Image(systemName: categoryIcon(budget.categoryName)).foregroundStyle(AppColors.accentPurple).frame(width: 22)
                                Text(budget.categoryName).bold()
                                Spacer()
                                Text(formatCurrency(budget.spent))
                                Text("/").foregroundStyle(DS.textSecondary)
                                Text(formatCurrency(budget.monthlyLimit)).foregroundStyle(DS.textSecondary)
                            }
                            ProgressView(value: min(budget.percentage, 100), total: 100)
                                .tint(budget.status == .exceeded ? .red : budget.status == .warning ? .orange : .green)
                            Text(budget.status == .exceeded ? "Over budget!" :
                                    budget.status == .warning ? "Getting close" : "On track")
                                .font(.caption)
                                .foregroundStyle(budget.status == .exceeded ? .red : budget.status == .warning ? .orange : .green)
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                Task { await viewModel.deleteBudget(budget.id) }
                            } label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                }
            }
        }
        .navigationTitle("Budgets")
        .alert("Error", isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("OK") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showAdd = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAdd) {
            NavigationStack {
                Form {
                    Picker("Category", selection: $selectedCatId) {
                        Text("Select").tag("")
                        ForEach(viewModel.categories) { cat in
                            Label(cat.name, systemImage: categoryIcon(cat.name, storedIcon: cat.icon)).tag(cat.id)
                        }
                    }
                    TextField("Monthly Limit", text: $limitStr)
                        .keyboardType(.decimalPad)
                }
                .navigationTitle("Add Budget")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showAdd = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add") {
                            if let limit = Double(limitStr), let cat = viewModel.categories.first(where: { $0.id == selectedCatId }) {
                                let catId = cat.id
                                let catName = cat.name
                                Task {
                                    await viewModel.addBudget(categoryId: catId, categoryName: catName, limit: limit)
                                    showAdd = false; limitStr = ""; selectedCatId = ""
                                }
                            }
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .task { await viewModel.load() }
    }
}
