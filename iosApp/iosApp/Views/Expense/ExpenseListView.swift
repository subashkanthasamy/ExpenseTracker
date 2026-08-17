import SwiftUI
import Shared

struct ExpenseListView: View {
    @Bindable var viewModel: ExpenseListViewModel
    /// Optional so the view still renders before the role resolves; nil means "assume the
    /// least", which hides the destructive affordances rather than showing ones that fail.
    var sessionRole: SessionRole?
    var onAdd: () -> Void
    var onEdit: (String) -> Void

    @State private var showFilters = false

    var body: some View {
        VStack(spacing: 10) {
            // Nothing recorded yet means nothing to narrow.
            if !viewModel.expenses.isEmpty {
                searchRow
                ActiveFilterChips(viewModel: viewModel)
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
                                let mine = sessionRole?.canEdit(expense) ?? false
                                ExpenseRow(expense: expense)
                                    .contentShape(Rectangle())
                                    .onTapGesture { if mine { onEdit(expense.id) } }
                                    .swipeActions(edge: .trailing) {
                                        // Only rows you may actually change offer the action.
                                        if mine {
                                            Button(role: .destructive) {
                                                Task { await viewModel.delete(expense) }
                                            } label: { Label("Delete", systemImage: "trash") }
                                        }
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
                if sessionRole?.canAddExpense ?? false {
                    Button(action: onAdd) { Image(systemName: "plus") }
                }
            }
        }
        .sheet(isPresented: $showFilters) {
            ExpenseFilterSheet(viewModel: viewModel)
        }
        .task { await viewModel.load() }
        .onDisappear { viewModel.cleanup() }
    }

    /// Search plus the filter button. Date / category / person live in a sheet rather than as
    /// three stacked chip rows, which pushed the expenses themselves down the screen.
    private var searchRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(DS.textSecondary)
                TextField("Search", text: $viewModel.searchQuery)
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

            Button { showFilters = true } label: {
                Image(systemName: "slider.horizontal.3")
                    .foregroundStyle(viewModel.hasChipFilters ? .white : DS.textSecondary)
                    .frame(width: 44, height: 44)
                    .background(
                        viewModel.hasChipFilters ? AnyShapeStyle(DS.accent) : AnyShapeStyle(DS.elevated),
                        in: RoundedRectangle(cornerRadius: DS.tileRadius)
                    )
            }
            .buttonStyle(.plain)
        }
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

/// A removable chip per active filter, so what's on is visible without opening the sheet.
private struct ActiveFilterChips: View {
    @Bindable var viewModel: ExpenseListViewModel

    var body: some View {
        // The search field already shows its own text, so it doesn't get a chip.
        let active = activeFilters
        if !active.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(active.enumerated()), id: \.offset) { _, item in
                        Button(action: item.remove) {
                            HStack(spacing: 4) {
                                Text(item.label)
                                    .font(.subheadline)
                                Image(systemName: "xmark")
                                    .font(.caption2.weight(.bold))
                            }
                            .foregroundStyle(DS.accent)
                            .padding(.leading, 14)
                            .padding(.trailing, 10)
                            .padding(.vertical, 6)
                            .background(DS.accent.opacity(0.15), in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, DS.screenPadding)
            }
        }
    }

    private var activeFilters: [(label: String, remove: () -> Void)] {
        var items: [(label: String, remove: () -> Void)] = []
        if viewModel.dateRange != .all {
            items.append((viewModel.dateRange.label, { viewModel.dateRange = .all }))
        }
        if let option = viewModel.categoryOptions.first(where: { $0.id == viewModel.categoryFilter }) {
            items.append((option.label, { viewModel.categoryFilter = nil }))
        }
        if let option = viewModel.personOptions.first(where: { $0.id == viewModel.personFilter }) {
            items.append((option.label, { viewModel.personFilter = nil }))
        }
        if let method = viewModel.paymentMethodFilter {
            items.append(("\(method.emoji) \(method.label)", { viewModel.paymentMethodFilter = nil }))
        }
        return items
    }
}

private struct ExpenseFilterSheet: View {
    @Bindable var viewModel: ExpenseListViewModel
    @Environment(\.dismiss) private var dismiss

    /// Kotlin enums export as NSObject singletons: `entries` is a real Swift array and `==`
    /// works, but `switch` pattern-matching does not.
    private let ranges = DateRangeFilter.entries

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    section("Date") {
                        chipRow(
                            labels: ranges.map { $0 == .all ? "All dates" : $0.label },
                            isSelected: { [ranges] in viewModel.dateRange == ranges[$0] },
                            select: { [ranges] in viewModel.dateRange = ranges[$0] }
                        )
                    }

                    if !viewModel.categoryOptions.isEmpty {
                        section("Category") {
                            optionRow(
                                allLabel: "All categories",
                                options: viewModel.categoryOptions,
                                selectedId: viewModel.categoryFilter,
                                select: { viewModel.categoryFilter = $0 }
                            )
                        }
                    }

                    section("Paid with") {
                        let methods = PaymentMethod.companion.selectable
                        chipRow(
                            labels: ["Any method"] + methods.map { $0.label },
                            isSelected: { [methods] index in
                                index == 0
                                    ? viewModel.paymentMethodFilter == nil
                                    : viewModel.paymentMethodFilter == methods[index - 1]
                            },
                            select: { [methods] index in
                                viewModel.paymentMethodFilter = index == 0 ? nil : methods[index - 1]
                            }
                        )
                    }

                    // A single member makes the filter a guaranteed no-op.
                    if viewModel.personOptions.count > 1 {
                        section("Added by") {
                            optionRow(
                                allLabel: "Everyone",
                                options: viewModel.personOptions,
                                selectedId: viewModel.personFilter,
                                select: { viewModel.personFilter = $0 }
                            )
                        }
                    }
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if viewModel.isFiltering {
                        Button("Clear all") { viewModel.clearFilters() }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func section<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.footnote)
                .foregroundStyle(DS.textSecondary)
                .padding(.horizontal, DS.screenPadding)
            content()
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
