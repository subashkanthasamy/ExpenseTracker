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

            "Expense Report".draw(at: CGPoint(x: margin, y: y), withAttributes: title)
            y += 30
            let total = expenses.reduce(0.0) { $0 + $1.amount }
            "Total: \(total)   (\(expenses.count) expenses)".draw(at: CGPoint(x: margin, y: y), withAttributes: body)
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

    /// Parses a CSV previously produced by `exportCSV` (or Android's exporter).
    /// Rows that don't parse are skipped rather than aborting the whole import.
    func parseCSV(at url: URL, householdId: String, addedBy: String, addedByName: String) throws -> (expenses: [Expense], result: ImportResult) {
        let text = try String(contentsOf: url, encoding: .utf8)
        let f = Self.dateFormatter
        var expenses: [Expense] = []
        var skipped = 0

        for (index, rawLine) in text.split(separator: "\n", omittingEmptySubsequences: true).enumerated() {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }
            // Skip a header row if present.
            if index == 0, line.lowercased().hasPrefix("date,") { continue }

            let cols = Self.splitCSVLine(line)
            guard cols.count >= 3,
                  let date = f.date(from: cols[0]),
                  let amount = Double(cols[2]) else {
                skipped += 1
                continue
            }
            expenses.append(Expense(
                id: UUID().uuidString,
                householdId: householdId,
                amount: amount,
                categoryId: "",
                categoryName: cols[1],
                date: date,
                notes: cols.count > 3 ? cols[3] : "",
                addedBy: addedBy,
                addedByName: addedByName
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
