import SwiftUI
import Shared
import UniformTypeIdentifiers

struct SettingsView: View {
    @Bindable var viewModel: SettingsViewModel
    @Bindable var prefs: AppPreferences

    @State private var showResetConfirm = false
    @State private var showImporter = false
    @State private var showExportOptions = false

    var body: some View {
        List {
            Section("Appearance") {
                Picker("Theme", selection: $prefs.themeMode) {
                    Text("System").tag(Int(ThemePreferencesCompanion.shared.THEME_SYSTEM))
                    Text("Light").tag(Int(ThemePreferencesCompanion.shared.THEME_LIGHT))
                    Text("Dark").tag(Int(ThemePreferencesCompanion.shared.THEME_DARK))
                }
            }

            Section {
                Toggle(isOn: Binding(
                    get: { prefs.biometricEnabled },
                    set: { newValue in
                        Task { await viewModel.setBiometricEnabled(newValue, prefs: prefs) }
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Biometric lock")
                        Text(viewModel.biometricAvailable
                             ? "Require Face ID or Touch ID to open the app"
                             : "Not available on this device")
                            .font(.caption)
                            .foregroundStyle(DS.textSecondary)
                    }
                }
                .disabled(!viewModel.biometricAvailable)
            } header: {
                Text("Security")
            }

            Section("Data") {
                Button {
                    showExportOptions = true
                } label: {
                    settingsRow("Export data", "Export expenses as CSV or PDF", "square.and.arrow.up")
                }

                Button {
                    showImporter = true
                } label: {
                    settingsRow("Import data", "Import expenses from a CSV file", "square.and.arrow.down")
                }
            }

            Section {
                Button(role: .destructive) {
                    showResetConfirm = true
                } label: {
                    settingsRow("Delete all expenses", "Delete every expense in this household", "trash")
                }
            }

            Section {
                LabeledContent("Version", value: "1.0.0")
            } footer: {
                // Be explicit rather than letting users hunt for a missing feature.
                Text("Automatic SMS import is only available on Android, because iOS doesn't let apps read your messages. You can still paste a bank SMS when you add an expense.")
            }
        }
        .navigationTitle("Settings")
        .overlay {
            if viewModel.isBusy { ProgressView().controlSize(.large) }
        }
        .confirmationDialog("Export format", isPresented: $showExportOptions, titleVisibility: .visible) {
            Button("CSV") { Task { await viewModel.export(as: .csv) } }
            Button("PDF") { Task { await viewModel.export(as: .pdf) } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog(
            "Delete all expenses?",
            isPresented: $showResetConfirm,
            titleVisibility: .visible
        ) {
            Button("Delete all", role: .destructive) { Task { await viewModel.resetAllExpenses() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Every expense in this household will be deleted. This can't be undone.")
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.commaSeparatedText, .text]) { result in
            switch result {
            case .success(let url):
                Task { await viewModel.importCSV(from: url) }
            case .failure(let err):
                viewModel.error = "Couldn't open that file. Try again."
            }
        }
        .sheet(isPresented: Binding(
            get: { viewModel.exportedFile != nil },
            set: { if !$0 { viewModel.exportedFile = nil } }
        )) {
            if let file = viewModel.exportedFile {
                ShareSheet(items: [file])
            }
        }
        .alert("Something went wrong", isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("Close") { viewModel.error = nil }
        } message: {
            Text(viewModel.error ?? "")
        }
        .alert("Done", isPresented: Binding(
            get: { viewModel.statusMessage != nil },
            set: { if !$0 { viewModel.statusMessage = nil } }
        )) {
            Button("Close") { viewModel.statusMessage = nil }
        } message: {
            Text(viewModel.statusMessage ?? "")
        }
    }

    private func settingsRow(_ title: String, _ subtitle: String, _ icon: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(subtitle).font(.caption).foregroundStyle(DS.textSecondary)
            }
            Spacer()
        }
    }
}

/// UIActivityViewController bridge — SwiftUI has no native share sheet for file URLs.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
