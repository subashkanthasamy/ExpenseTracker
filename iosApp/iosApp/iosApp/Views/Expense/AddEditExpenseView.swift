import SwiftUI

struct AddEditExpenseView: View {
    @Bindable var viewModel: AddEditExpenseViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showDatePicker = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Amount") {
                    TextField("0.00", text: $viewModel.amount)
                        .keyboardType(.decimalPad)
                        .font(.title)
                }

                Section("Category") {
                    if viewModel.categories.isEmpty {
                        ProgressView("Loading categories...")
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 70))], spacing: 12) {
                            ForEach(viewModel.categories) { cat in
                                VStack(spacing: 4) {
                                    Text(categoryEmoji(cat.name))
                                        .font(.title2)
                                        .frame(width: 48, height: 48)
                                        .background(viewModel.selectedCategory?.id == cat.id ? AppColors.accentPurple.opacity(0.2) : Color.gray.opacity(0.1))
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(viewModel.selectedCategory?.id == cat.id ? AppColors.accentPurple : .clear, lineWidth: 2)
                                        )
                                    Text(cat.name)
                                        .font(.caption2)
                                        .lineLimit(1)
                                }
                                .onTapGesture { viewModel.selectedCategory = cat }
                            }
                        }
                    }
                }

                Section("Date") {
                    DatePicker("Date", selection: $viewModel.date, displayedComponents: .date)
                }

                Section("Notes") {
                    TextField("Optional notes", text: $viewModel.notes)
                }

                if let error = viewModel.error {
                    Section { Text(error).foregroundStyle(.red) }
                }
            }
            .navigationTitle(viewModel.isEditing ? "Edit Expense" : "Add Expense")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(viewModel.isEditing ? "Update" : "Add") {
                        Task {
                            if await viewModel.save() { dismiss() }
                        }
                    }
                    .disabled(viewModel.isLoading)
                }
            }
            .task { await viewModel.loadCategories() }
        }
    }
}
