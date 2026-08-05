import SwiftUI
import Shared

struct RecurringView: View {
    @Bindable var viewModel: RecurringViewModel
    @State private var showAdd = false

    var body: some View {
        List {
            if viewModel.isLoading {
                ProgressView("Loading…")
            } else if viewModel.items.isEmpty {
                ContentUnavailableView(
                    "No recurring expenses",
                    systemImage: "arrow.clockwise.circle",
                    description: Text("Add rent, subscriptions or EMIs and they'll be created automatically.")
                )
            } else {
                ForEach(viewModel.items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(item.categoryName).bold()
                            Spacer()
                            Text(formatCurrency(item.amount)).bold()
                        }
                        HStack(spacing: 8) {
                            Text(item.frequencyLabel)
                                .font(.caption)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(AppColors.accentPurple.opacity(0.15))
                                .clipShape(Capsule())
                            if !item.notes.isEmpty {
                                Text(item.notes).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Toggle("", isOn: Binding(
                                get: { item.isActive },
                                set: { newValue in Task { await viewModel.setActive(newValue, for: item) } }
                            ))
                            .labelsHidden()
                        }
                    }
                    .swipeActions {
                        Button("Delete", role: .destructive) {
                            Task { await viewModel.delete(item) }
                        }
                    }
                }
            }

            if viewModel.generatedCount > 0 {
                Section {
                    Label("Created \(viewModel.generatedCount) expense(s) that were due",
                          systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.caption)
                }
            }
        }
        .navigationTitle("Recurring")
        .toolbar {
            Button { showAdd = true } label: { Image(systemName: "plus") }
        }
        .task { await viewModel.load() }
        .sheet(isPresented: $showAdd) {
            AddRecurringSheet(viewModel: viewModel)
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("OK") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
    }
}

private struct AddRecurringSheet: View {
    @Bindable var viewModel: RecurringViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var amount = ""
    @State private var notes = ""
    @State private var selectedCategory: Shared.Category?
    @State private var frequencyIndex = 2  // monthly
    @State private var dayOfMonth = 1
    @State private var weekday = 2
    @State private var startDate = Date()

    private let frequencies = ["Daily", "Weekly", "Monthly", "Yearly"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Amount") {
                    TextField("0.00", text: $amount).keyboardType(.decimalPad)
                }

                Section("Category") {
                    Picker("Category", selection: $selectedCategory) {
                        Text("Select").tag(nil as Shared.Category?)
                        ForEach(viewModel.categories) { cat in
                            Text(cat.name).tag(cat as Shared.Category?)
                        }
                    }
                }

                Section("Repeats") {
                    Picker("Frequency", selection: $frequencyIndex) {
                        ForEach(frequencies.indices, id: \.self) { i in
                            Text(frequencies[i]).tag(i)
                        }
                    }
                    if frequencyIndex == 1 {
                        Picker("Day of week", selection: $weekday) {
                            ForEach(1...7, id: \.self) { d in
                                Text(Calendar.current.weekdaySymbols[d - 1]).tag(d)
                            }
                        }
                    } else if frequencyIndex == 2 || frequencyIndex == 3 {
                        Picker("Day of month", selection: $dayOfMonth) {
                            ForEach(1...31, id: \.self) { d in Text("\(d)").tag(d) }
                        }
                    }
                    DatePicker("Starts", selection: $startDate, displayedComponents: .date)
                }

                Section("Note") {
                    TextField("Rent, Netflix, EMI…", text: $notes)
                }
            }
            .navigationTitle("Add Recurring")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            await viewModel.add(
                                amount: Double(amount) ?? 0,
                                category: selectedCategory,
                                notes: notes,
                                frequencyIndex: frequencyIndex,
                                dayOfMonth: dayOfMonth,
                                weekday: weekday,
                                startDate: startDate
                            )
                            dismiss()
                        }
                    }
                    .disabled(Double(amount) == nil || selectedCategory == nil)
                }
            }
            .task { await viewModel.loadCategories() }
        }
    }
}
