import SwiftUI
import Shared

struct ExpenseListView: View {
    @Bindable var viewModel: ExpenseListViewModel
    var onAdd: () -> Void
    var onEdit: (String) -> Void

    var body: some View {
        VStack(spacing: 12) {
            // Nothing recorded yet means nothing to narrow.
            if !viewModel.expenses.isEmpty {
                searchField
                ExpenseFilterChips(viewModel: viewModel)
            }

            if viewModel.isLoading {
                Spacer()
                ProgressView()
                Spacer()
            } else if viewModel.filteredExpenses.isEmpty {
                Spacer()
                emptyState
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
        .task { await viewModel.load() }
        .onDisappear { viewModel.cleanup() }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(DS.textSecondary)
            TextField("Search notes, category, person or amount", text: $viewModel.searchQuery)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
            if !viewModel.searchQuery.isEmpty {
                Button { viewModel.searchQuery = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(DS.textSecondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(DS.elevated, in: RoundedRectangle(cornerRadius: DS.tileRadius))
        .padding(.horizontal, DS.screenPadding)
    }

    /// `expenses` vs `filteredExpenses` is the difference between "nothing recorded" and
    /// "your filters excluded everything" — the screen used to show the former for both.
    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: viewModel.expenses.isEmpty ? "tray" : "line.3.horizontal.decrease.circle")
                .font(.system(size: 48))
                .foregroundStyle(DS.textSecondary)
            if viewModel.expenses.isEmpty {
                Text("No expenses yet").foregroundStyle(DS.textSecondary)
            } else {
                Text("No matching expenses").foregroundStyle(DS.textPrimary)
                Text("None of your \(viewModel.expenses.count) expenses match these filters")
                    .font(.footnote)
                    .foregroundStyle(DS.textSecondary)
                    .multilineTextAlignment(.center)
                Button("Clear filters") { viewModel.clearFilters() }
                    .foregroundStyle(DS.accent)
            }
        }
        .padding(.horizontal, DS.screenPadding)
    }
}

/// Date-range, category and person chip rows. Selections live on the view model, never in
/// local `@State`, so the list and the chips can't disagree.
private struct ExpenseFilterChips: View {
    @Bindable var viewModel: ExpenseListViewModel

    /// Kotlin enums export as NSObject singletons: `entries` is a real Swift array and `==`
    /// works, but `switch` pattern-matching does not.
    private let ranges = DateRangeFilter.entries

    var body: some View {
        VStack(spacing: 8) {
            chipRow(
                labels: ranges.map { $0 == .all ? "All dates" : $0.label },
                isSelected: { [ranges] in viewModel.dateRange == ranges[$0] },
                select: { [ranges] in viewModel.dateRange = ranges[$0] }
            )

            if !viewModel.categoryOptions.isEmpty {
                optionRow(
                    allLabel: "All categories",
                    options: viewModel.categoryOptions,
                    selectedId: viewModel.categoryFilter,
                    select: { viewModel.categoryFilter = $0 }
                )
            }

            // A single member makes the filter a guaranteed no-op.
            if viewModel.personOptions.count > 1 {
                optionRow(
                    allLabel: "Everyone",
                    options: viewModel.personOptions,
                    selectedId: viewModel.personFilter,
                    select: { viewModel.personFilter = $0 }
                )
            }
        }
    }

    private func optionRow(
        allLabel: String,
        options: [FilterOption],
        selectedId: String?,
        select: @escaping (String?) -> Void
    ) -> some View {
        // Index 0 is the "all" chip, so option i sits at chip i + 1.
        chipRow(
            labels: [allLabel] + options.map(\.label),
            isSelected: { index in
                index == 0 ? selectedId == nil : selectedId == options[index - 1].id
            },
            select: { index in
                select(index == 0 ? nil : options[index - 1].id)
            }
        )
    }

    private func chipRow(
        labels: [String],
        isSelected: @escaping (Int) -> Bool,
        select: @escaping (Int) -> Void
    ) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                    let selected = isSelected(index)
                    Button { select(index) } label: {
                        Text(label)
                            .font(.subheadline.weight(selected ? .semibold : .regular))
                            .foregroundStyle(selected ? .white : DS.textSecondary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background {
                                if selected {
                                    Capsule().fill(DS.accent)
                                } else {
                                    Capsule().stroke(DS.stroke, lineWidth: 1)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, DS.screenPadding)
        }
    }
}
