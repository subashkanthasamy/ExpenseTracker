import Foundation
import PDFKit
import Shared
import UIKit

/// CSV / PDF export and CSV import, mirroring Android's ExportExpensesUseCase and
/// ImportExpensesUseCase. Writes into the temporary directory and hands back a URL for
/// the share sheet.
struct ExportService {

    enum Format {
        case csv, pdf
    }

    struct ImportResult {
        let imported: Int
        let skipped: Int
    }

    private static let header = ["Date", "Category", "Amount", "Payment Method", "Notes", "Added By"]

    private static var dateFormatter: DateFormatter {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }

    // MARK: - Export

    func export(_ expenses: [Expense], as format: Format) throws -> URL {
        switch format {
        case .csv: return try exportCSV(expenses)
        case .pdf: return try exportPDF(expenses)
        }
    }

    private func exportCSV(_ expenses: [Expense]) throws -> URL {
        let f = Self.dateFormatter
        var rows = [Self.header.joined(separator: ",")]
        for e in expenses {
            rows.append([
                f.string(from: e.dateValue),
                Self.escape(e.categoryName),
                String(e.amount),
                Self.escape(e.paymentMethod.label),
                Self.escape(e.notes),
                Self.escape(e.addedByName),
            ].joined(separator: ","))
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("expenses-\(f.string(from: Date())).csv")
        try rows.joined(separator: "\n").write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    private func exportPDF(_ expenses: [Expense]) throws -> URL {
        let f = Self.dateFormatter
        // A4 at 72dpi.
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        let margin: CGFloat = 40
        let lineHeight: CGFloat = 20

        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("expenses-\(f.string(from: Date())).pdf")

        let title = [NSAttributedString.Key.font: UIFont.boldSystemFont(ofSize: 18)]
        let bold = [NSAttributedString.Key.font: UIFont.boldSystemFont(ofSize: 11)]
        let body = [NSAttributedString.Key.font: UIFont.systemFont(ofSize: 11)]
        let columns: [CGFloat] = [margin, margin + 90, margin + 230, margin + 310]

        try renderer.writePDF(to: url) { ctx in
            ctx.beginPage()
            var y = margin

            "Expense report".draw(at: CGPoint(x: margin, y: y), withAttributes: title)
            y += 30
            let total = expenses.reduce(0.0) { $0 + $1.amount }
            "Total: \(total)   (\(expenses.count) \(expenses.count == 1 ? "expense" : "expenses"))".draw(at: CGPoint(x: margin, y: y), withAttributes: body)
            y += 28

            for (i, h) in ["Date", "Category", "Amount", "Notes"].enumerated() {
                h.draw(at: CGPoint(x: columns[i], y: y), withAttributes: bold)
            }
            y += lineHeight

            for e in expenses {
                if y > pageRect.height - margin {
                    ctx.beginPage()
                    y = margin
                }
                let cells = [
                    f.string(from: e.dateValue),
                    e.categoryName,
                    String(format: "%.2f", e.amount),
                    e.notes,
                ]
                for (i, cell) in cells.enumerated() {
                    cell.draw(at: CGPoint(x: columns[i], y: y), withAttributes: body)
                }
                y += lineHeight
            }
        }
        return url
    }

    // MARK: - Import

    enum ImportError: LocalizedError {
        case missingColumns

        var errorDescription: String? {
            "This file isn't in the expected format. It needs Date, Amount and Category columns."
        }
    }

    /// Parses a CSV produced by this app's exporter, Android's or the web's. Columns are
    /// matched by header name, since the three exporters order them differently; a file
    /// with no header row falls back to the old positional layout (date, category, amount,
    /// notes). Rows that don't parse are skipped rather than aborting the whole import.
    func parseCSV(at url: URL, householdId: String, addedBy: String, addedByName: String) throws -> (expenses: [Expense], result: ImportResult) {
        let text = try String(contentsOf: url, encoding: .utf8)
        let f = Self.dateFormatter
        var expenses: [Expense] = []
        var skipped = 0

        let lines = text.split(separator: "\n", omittingEmptySubsequences: true)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard let first = lines.first else {
            return (expenses, ImportResult(imported: 0, skipped: 0))
        }

        // Header present if the first row starts with "date" — the same test as before.
        // A byte-order mark (spreadsheet apps add one) would otherwise hide the header.
        let names = Self.splitCSVLine(first)
            .map { $0.trimmingCharacters(in: CharacterSet.whitespaces.union(["\u{FEFF}"])).lowercased() }
        let hasHeader = names.first == "date"
        let column: (String) -> Int? = { name in names.firstIndex(of: name) }
        let dateCol, categoryCol, amountCol: Int
        let notesCol, methodCol, scopeCol: Int?
        if hasHeader {
            guard let d = column("date"), let a = column("amount"), let c = column("category") else {
                throw ImportError.missingColumns
            }
            dateCol = d; amountCol = a; categoryCol = c
            notesCol = column("notes")
            methodCol = column("payment method")
            scopeCol = column("visibility")
        } else {
            dateCol = 0; categoryCol = 1; amountCol = 2
            notesCol = 3; methodCol = nil; scopeCol = nil
        }
        let field: ([String], Int?) -> String = { cols, i in
            guard let i, i < cols.count else { return "" }
            return cols[i].trimmingCharacters(in: .whitespaces)
        }

        for line in lines.dropFirst(hasHeader ? 1 : 0) {
            let cols = Self.splitCSVLine(line)
            guard let date = f.date(from: field(cols, dateCol)),
                  let amount = Double(field(cols, amountCol)) else {
                skipped += 1
                continue
            }
            let method = field(cols, methodCol).lowercased()
            expenses.append(Expense(
                id: UUID().uuidString,
                householdId: householdId,
                amount: amount,
                categoryId: "",
                categoryName: field(cols, categoryCol),
                date: date,
                notes: field(cols, notesCol),
                addedBy: addedBy,
                addedByName: addedByName,
                paymentMethod: PaymentMethod.companion.selectable.first {
                    $0.label.lowercased() == method || $0.wire == method
                } ?? PaymentMethod.unspecified,
                scope: field(cols, scopeCol).lowercased() == "personal" ? ExpenseScope.personal : ExpenseScope.shared
            ))
        }
        return (expenses, ImportResult(imported: expenses.count, skipped: skipped))
    }

    // MARK: - CSV helpers

    private static func escape(_ value: String) -> String {
        guard value.contains(",") || value.contains("\"") || value.contains("\n") else { return value }
        return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    /// Minimal RFC4180-style split that respects quoted fields containing commas.
    private static func splitCSVLine(_ line: String) -> [String] {
        var fields: [String] = []
        var current = ""
        var inQuotes = false
        var iterator = line.makeIterator()
        var pending: Character?

        while let ch = pending ?? iterator.next() {
            pending = nil
            if inQuotes {
                if ch == "\"" {
                    if let next = iterator.next() {
                        if next == "\"" { current.append("\"") } else { inQuotes = false; pending = next }
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(ch)
                }
            } else if ch == "\"" {
                inQuotes = true
            } else if ch == "," {
                fields.append(current)
                current = ""
            } else {
                current.append(ch)
            }
        }
        fields.append(current)
        return fields
    }
}
