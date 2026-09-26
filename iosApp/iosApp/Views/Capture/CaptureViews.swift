import PhotosUI
import Shared
import SwiftUI

// MARK: - Receipt scanning

struct ReceiptScannerView: View {
    /// Called with whatever could be read off the receipt.
    let onScanned: (ReceiptResult) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var image: UIImage?
    @State private var result: ReceiptResult?
    @State private var isScanning = false
    @State private var error: String?

    private let scanner = ReceiptScanService()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let image {
                        Image(uiImage: image)
                            .resizable().scaledToFit()
                            .frame(maxHeight: 220)
                    }
                    Button {
                        showCamera = true
                    } label: { Label("Take photo", systemImage: "camera.fill") }

                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label("Choose from library", systemImage: "photo.on.rectangle")
                    }
                }

                if isScanning {
                    Section { HStack { ProgressView(); Text("Reading receipt…") } }
                }

                if let result {
                    Section("Found") {
                        LabeledContent("Merchant", value: result.merchant ?? "—")
                        LabeledContent("Amount", value: result.amount.map { formatCurrency($0.doubleValue) } ?? "—")
                        LabeledContent("Date", value: result.date.map {
                            Date(epochMillis: $0.int64Value).formatted(date: .abbreviated, time: .omitted)
                        } ?? "—")
                    }
                    Section {
                        Button("Use these details") {
                            onScanned(result)
                            dismiss()
                        }
                        .disabled(result.amount == nil)
                    } footer: {
                        if result.amount == nil {
                            Text("Couldn't find an amount. Try a sharper photo, or enter the amount yourself.")
                        }
                    }
                    Section("Text on the receipt") {
                        Text(result.rawText.isEmpty ? "No text found" : result.rawText)
                            .font(.caption.monospaced())
                            .foregroundStyle(DS.textSecondary)
                    }
                }
            }
            .navigationTitle("Scan receipt")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker { picked in
                    image = picked
                    Task { await scan(picked) }
                }
            }
            .onChange(of: pickerItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let picked = UIImage(data: data) {
                        image = picked
                        await scan(picked)
                    }
                }
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { error != nil }, set: { if !$0 { error = nil } }
            )) {
                Button("Close") { error = nil }
            } message: { Text(error ?? "") }
        }
    }

    private func scan(_ image: UIImage) async {
        isScanning = true
        do {
            result = try await scanner.scan(image)
        } catch {
            self.error = error.localizedDescription
        }
        isScanning = false
    }
}

/// UIImagePickerController bridge — SwiftUI still has no native camera capture view.
struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage { parent.onCapture(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

// MARK: - Voice entry

struct VoiceEntryView: View {
    /// Amount and optional category hint, as understood by the shared parser.
    let onCaptured: (Double, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var voice = VoiceInputService()

    var body: some View {
        NavigationStack {
            VStack(spacing: 28) {
                Spacer()

                Button {
                    Task {
                        if voice.isListening { voice.stop() } else { await voice.start() }
                    }
                } label: {
                    Image(systemName: voice.isListening ? "stop.circle.fill" : "mic.circle.fill")
                        .font(.system(size: 92))
                        .foregroundStyle(voice.isListening ? AnyShapeStyle(.red) : AnyShapeStyle(AppColors.gradient))
                }

                Text(voice.isListening ? "Listening…" : "Tap to speak")
                    .foregroundStyle(DS.textSecondary)

                if !voice.transcript.isEmpty {
                    Text("\u{201C}\(voice.transcript)\u{201D}")
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                if let parsed = voice.parsed, let amount = parsed.amount {
                    VStack(spacing: 6) {
                        Text(formatCurrency(amount.doubleValue)).font(.largeTitle).bold()
                        if let hint = parsed.categoryHint {
                            Text(hint.capitalized).foregroundStyle(DS.textSecondary)
                        }
                    }
                    Button("Use this") {
                        voice.stop()
                        onCaptured(amount.doubleValue, parsed.categoryHint)
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppColors.accentPurple)
                }

                switch voice.state {
                case .denied(let message), .failed(let message):
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(.orange)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                default:
                    EmptyView()
                }

                Spacer()
                Text("Try saying \u{201C}spent four hundred on groceries\u{201D}")
                    .font(.caption).foregroundStyle(.tertiary)
            }
            .navigationTitle("Add by voice")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { voice.stop(); dismiss() }
                }
            }
            .onDisappear { voice.stop() }
        }
    }
}
