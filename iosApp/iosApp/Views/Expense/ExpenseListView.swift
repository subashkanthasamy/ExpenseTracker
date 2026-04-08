import SwiftUI

struct ExpenseListView: View {
    @Bindable var viewModel: ExpenseListViewModel
    var onAdd: () -> Void
    var onEdit: (String) -> Void

    var body: some View {
        VStack {
            TextField("Search expenses...", text: $viewModel.searchQuery)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal)

            if viewModel.isLoading {
                Spacer()
                ProgressView()
                Spacer()
            } else if viewModel.filteredExpenses.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "tray").font(.system(size: 48)).foregroundStyle(.secondary)
                    Text("No expenses yet").foregroundStyle(.secondary)
                }
                Spacer()
            } else {
                List {
                    ForEach(viewModel.groupedExpenses, id: \.0) { date, expenses in
                        Section(date) {
                            ForEach(expenses) { expense in
                                ExpenseRow(expense: expense)
                                    .contentShape(Rectangle())
                                    .onTapGesture { onEdit(expense.id) }
                                    .swipeActions(edge: .trailing) {
                                        Button(role: .destructive) {
                                            Task { await viewModel.delete(expense) }
                                        } label: { Label("Delete", systemImage: "trash") }
                                    }
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("Expenses")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button(action: onAdd) { Image(systemName: "plus") }
            }
        }
        .onAppear { viewModel.load() }
        .onDisappear { viewModel.cleanup() }
    }
}
