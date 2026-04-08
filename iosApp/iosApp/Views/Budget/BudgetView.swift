import SwiftUI

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
                    Image(systemName: "chart.pie").font(.system(size: 48)).foregroundStyle(.secondary)
                    Text("No budgets set").foregroundStyle(.secondary)
                    Text("Tap + to add a monthly budget").font(.caption).foregroundStyle(.secondary)
                }
            } else {
                List {
                    ForEach(viewModel.budgets) { budget in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(categoryEmoji(budget.categoryName))
                                Text(budget.categoryName).bold()
                                Spacer()
                                Text(formatCurrency(budget.spent))
                                Text("/").foregroundStyle(.secondary)
                                Text(formatCurrency(budget.monthlyLimit)).foregroundStyle(.secondary)
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
                            Text("\(categoryEmoji(cat.name)) \(cat.name)").tag(cat.id)
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
                                Task { await viewModel.addBudget(categoryId: cat.id, categoryName: cat.name, limit: limit) }
                            }
                            showAdd = false; limitStr = ""; selectedCatId = ""
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .task { await viewModel.load() }
    }
}
