import Shared
import SwiftUI

/// Turns a bank SMS into an expense.
///
/// Android reads these messages automatically. iOS **cannot** — there is no API for an app
/// to read the SMS inbox, and `ILMessageFilterExtension` is deliberately built so it cannot
/// hand message content to its container app. So the message has to arrive by the user
/// pasting or sharing it.
///
/// The interpretation is not reimplemented here: `SmsTransactionParser` and
/// `SmsCategoryMatcher` come from `shared/`, so a given message produces exactly the same
/// amount, merchant and category on both platforms.
struct SmsImportView: View {
    /// amount, merchant (for the note) and a category hint, if one was recognised.
    let onImport: (Double, String?, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var body_ = ""
    @State private var sender = ""
    @State private var parsed: ParsedTransaction?
    @State private var categoryHint: String?
    @State private var didAttempt = false

    private let parser = SmsTransactionParser()
    private let matcher = SmsCategoryMatcher()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Sender (e.g. HDFCBK)", text: $sender)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    TextEditor(text: $body_)
                        .frame(minHeight: 120)
                        .font(.callout)
                    if body_.isEmpty {
                        Text("Paste the bank message here")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Bank message")
                } footer: {
                    Text("iOS doesn't allow apps to read your SMS, so paste the message (or share it to this app from Messages). Android imports these automatically.")
                }

                Section {
                    Button("Read message") { parse() }
                        .disabled(body_.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Button("Paste from clipboard") {
                        if let text = UIPasteboard.general.string {
                            body_ = text
                            parse()
                        }
                    }
                }

                if let parsed {
                    Section("Found") {
                        LabeledContent("Amount", value: formatCurrency(parsed.amount))
                        LabeledContent("Type", value: parsed.transactionType.name.capitalized)
                        LabeledContent("Merchant", value: parsed.merchant ?? "—")
                        if let account = parsed.cardOrAccount {
                            LabeledContent("Card / account", value: account)
                        }
                        LabeledContent("Category", value: categoryHint ?? "—")
                    }
                    Section {
                        Button("Use these details") {
                            onImport(parsed.amount, parsed.merchant, categoryHint)
                            dismiss()
                        }
                    }
                } else if didAttempt {
                    Section {
                        Label(
                            "That doesn't look like a transaction message — no amount could be read.",
                            systemImage: "exclamationmark.triangle"
                        )
                        .font(.caption)
                        .foregroundStyle(.orange)
                    }
                }
            }
            .navigationTitle("Import from SMS")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }

    private func parse() {
        didAttempt = true
        let result = parser.parse(
            sender: sender.isEmpty ? "BANK" : sender,
            body: body_,
            receivedTimestamp: Date().epochMillis
        )
        parsed = result
        categoryHint = result.flatMap { transaction in
            matcher.matchCategory(merchant: transaction.merchant, smsBody: transaction.rawMessage)
        }
    }
}
