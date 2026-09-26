import Foundation
import Shared
import UIKit
import Vision

/// Receipt OCR. Android uses CameraX + ML Kit; iOS uses the Vision framework.
///
/// Only the text extraction differs — the amount/date/merchant heuristics live in
/// `shared/` (`ReceiptTextParser`) so both platforms interpret a receipt identically.
struct ReceiptScanService {

    enum ScanError: LocalizedError {
        case noImageData
        case recognitionFailed(String)

        var errorDescription: String? {
            switch self {
            case .noImageData: return "Couldn't open that image. Try another photo."
            case .recognitionFailed: return "Couldn't read text from that photo. Try again with a clearer photo."
            }
        }
    }

    func scan(_ image: UIImage) async throws -> ReceiptResult {
        guard let cgImage = image.cgImage else { throw ScanError.noImageData }
        let text = try await recognizeText(in: cgImage)
        return ReceiptTextParser.shared.parse(text: text)
    }

    /// Rebuilds visual rows from Vision's observations.
    ///
    /// Vision reports each text region separately, so a receipt row like "Total 448.00"
    /// arrives as two observations and, if simply concatenated top-to-bottom, becomes two
    /// separate lines — leaving "Total" with no amount beside it. Android's ML Kit keeps
    /// rows intact, and the shared parser assumes the label and amount share a line, so
    /// group observations whose vertical centres are close and order each row left to right.
    static func reconstructLines(from observations: [VNRecognizedTextObservation]) -> String {
        struct Fragment {
            let text: String
            let midY: CGFloat
            let minX: CGFloat
            let height: CGFloat
        }

        let fragments: [Fragment] = observations.compactMap { obs in
            guard let text = obs.topCandidates(1).first?.string else { return nil }
            let box = obs.boundingBox
            return Fragment(text: text, midY: box.midY, minX: box.minX, height: box.height)
        }
        guard !fragments.isEmpty else { return "" }

        // Top of the image first (Vision's Y origin is bottom-left).
        let sorted = fragments.sorted { $0.midY > $1.midY }
        var rows: [[Fragment]] = []

        for fragment in sorted {
            // Same row if the vertical centres are within half a line height.
            if let last = rows.last,
               let reference = last.first,
               abs(reference.midY - fragment.midY) < max(reference.height, fragment.height) * 0.6 {
                rows[rows.count - 1].append(fragment)
            } else {
                rows.append([fragment])
            }
        }

        return rows
            .map { row in row.sorted { $0.minX < $1.minX }.map(\.text).joined(separator: " ") }
            .joined(separator: "\n")
    }

    private func recognizeText(in cgImage: CGImage) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    continuation.resume(throwing: ScanError.recognitionFailed(error.localizedDescription))
                    return
                }
                let observations = request.results as? [VNRecognizedTextObservation] ?? []
                continuation.resume(returning: Self.reconstructLines(from: observations))
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = false  // receipts are mostly numbers and codes

            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(throwing: ScanError.recognitionFailed(error.localizedDescription))
            }
        }
    }
}
