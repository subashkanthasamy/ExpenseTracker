import Shared
import SwiftUI

/// Add / edit a transaction, styled to `docs/Expense Tracker/Add Transaction.pdf`.
///
/// The mockups only specify a dark palette, so the `DS` tokens this screen uses are adaptive:
/// dark values come from the design, light values are derived. The screen therefore follows
/// the app's theme setting (Settings → Appearance) instead of forcing one appearance.
struct AddEditExpenseView: View {
    @Bindable var viewModel: AddEditExpenseViewModel
    @Environment(\.dismiss) var dismiss

    @State private var showScanner = false
    @State private var showVoice = false
    @State private var showSmsImport = false
    /// The design shows an Expense/Income selector. Only expenses are persisted today, so
    /// selecting Income explains itself rather than silently saving an expense.
    @State private var isExpense = true

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 4)

    var body: some View {
        ZStack {
            DS.canvas.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header

                    DSTypeToggle(isExpense: $isExpense)

                    if !isExpense {
                        Label(
                            "Income isn't stored yet — this screen currently records expenses only.",
                            systemImage: "info.circle"
                        )
                        .font(.caption)
                        .foregroundStyle(DS.textSecondary)
                    }

                    DSCaptureRow(icon: "mic.fill", title: "Add by Voice") { showVoice = true }

                    DSCaptureRow(
                        icon: "doc.text.viewfinder",
                        title: "Scan Receipt (OCR)",
                        dashed: true,
                        centered: true
                    ) { showScanner = true }

                    DSCaptureRow(icon: "text.bubble", title: "Import from bank SMS") {
                        showSmsImport = true
                    }

                    amountField
                    categoryGrid
                    paymentMethodRow
                    noteField
                    dateField

                    if let error = viewModel.error {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(DS.expense)
                    }

                    DSPrimaryButton(
                        title: viewModel.isEditing ? "Update Transaction" : "Add Transaction",
                        isBusy: viewModel.isLoading,
                        isEnabled: canSave
                    ) {
                        Task { if await viewModel.save() { dismiss() } }
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, DS.screenPadding)
                .padding(.bottom, 32)
            }
        }
        .task { await viewModel.loadCategories() }
        .sheet(isPresented: $showScanner) {
            ReceiptScannerView { result in
                if let amount = result.amount { viewModel.amount = String(amount.doubleValue) }
                if let millis = result.date { viewModel.date = Date(epochMillis: millis.int64Value) }
                if let merchant = result.merchant, viewModel.notes.isEmpty { viewModel.notes = merchant }
            }
        }
        .sheet(isPresented: $showSmsImport) {
            SmsImportView { amount, merchant, categoryHint, method in
                viewModel.amount = String(amount)
                if let merchant, viewModel.notes.isEmpty { viewModel.notes = merchant }
                applyCategoryHint(categoryHint)
                // Only overwrite when the message actually said — an unspecified inference
                // must not clobber the default the user would otherwise get.
                if method != PaymentMethod.unspecified { viewModel.paymentMethod = method }
            }
        }
        .sheet(isPresented: $showVoice) {
            VoiceEntryView { amount, categoryHint in
                viewModel.amount = String(amount)
                applyCategoryHint(categoryHint)
            }
        }
    }

    // MARK: - Sections

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                DSSectionLabel(text: "Add Entry")
                Text(viewModel.isEditing ? "Edit Transaction" : "New Transaction")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(DS.textPrimary)
            }
            Spacer()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DS.textSecondary)
                    .frame(width: 36, height: 36)
                    .background(DS.card)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Cancel")
        }
        .padding(.top, 12)
    }

    private var amountField: some View {
        DSCard {
            VStack(alignment: .leading, spacing: 8) {
                DSSectionLabel(text: "Amount ₹")
                TextField("0", text: $viewModel.amount)
                    .keyboardType(.decimalPad)
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(DS.textPrimary)
                    .tint(DS.accent)
            }
        }
    }

    private var categoryGrid: some View {
        VStack(alignment: .leading, spacing: 12) {
            DSSectionLabel(text: "Category")

            if viewModel.categories.isEmpty {
                DSCard {
                    HStack(spacing: 10) {
                        ProgressView().tint(DS.accent)
                        Text("Loading categories…")
                            .font(.subheadline)
                            .foregroundStyle(DS.textSecondary)
                    }
                }
            } else {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(viewModel.categories) { category in
                        DSCategoryTile(
                            symbol: categoryIcon(category.name, storedIcon: category.icon),
                            name: category.name,
                            isSelected: viewModel.selectedCategory?.id == category.id
                        ) {
                            viewModel.selectedCategory = category
                        }
                    }
                }
            }
        }
    }

    /// Sits between the category and the note because it is part of recording the
    /// transaction, not an afterthought like the note.
    private var paymentMethodRow: some View {
        VStack(alignment: .leading, spacing: 10) {
            DSSectionLabel(text: "Paid with")
            HStack(spacing: 8) {
                ForEach(PaymentMethod.companion.selectable, id: \.self) { method in
                    let selected = viewModel.paymentMethod == method
                    Button { viewModel.paymentMethod = method } label: {
                        Text("\(method.emoji)  \(method.label)")
                            .font(.subheadline.weight(selected ? .semibold : .regular))
                            .foregroundStyle(selected ? .white : DS.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(
                                selected ? AnyShapeStyle(DS.accent) : AnyShapeStyle(DS.elevated),
                                in: RoundedRectangle(cornerRadius: DS.tileRadius)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var noteField: some View {
        DSCard(padding: 14) {
            HStack(spacing: 12) {
                Image(systemName: "text.alignleft")
                    .font(.system(size: 15))
                    .foregroundStyle(DS.textLabel)
                TextField("Add a note…", text: $viewModel.notes)
                    .font(.system(size: 15))
                    .foregroundStyle(DS.textPrimary)
                    .tint(DS.accent)
            }
        }
    }

    private var dateField: some View {
        DSCard(padding: 14) {
            HStack(spacing: 12) {
                Image(systemName: "calendar")
                    .font(.system(size: 15))
                    .foregroundStyle(DS.accentSoft)
                DatePicker("", selection: $viewModel.date, displayedComponents: .date)
                    .labelsHidden()
                    .tint(DS.accent)
                Spacer()
            }
        }
    }

    // MARK: - Helpers

    private var canSave: Bool {
        Double(viewModel.amount) != nil && viewModel.selectedCategory != nil && isExpense
    }

    private func applyCategoryHint(_ hint: String?) {
        guard let hint else { return }
        viewModel.selectedCategory = viewModel.categories.first {
            $0.name.localizedCaseInsensitiveContains(hint)
        } ?? viewModel.selectedCategory
    }
}
