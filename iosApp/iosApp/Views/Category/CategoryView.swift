import SwiftUI
import Shared

struct CategoryView: View {
    @Bindable var viewModel: CategoryViewModel
    /// Shared household configuration is owner-level. Defaults to false so nothing the
    /// rules would reject is shown before the role resolves.
    var canManage: Bool = false
    @State private var showAdd = false
    @State private var newName = ""
    @State private var newIcon = "💳"

    var body: some View {
        List {
            if !viewModel.presetCategories.isEmpty {
                Section("Preset categories") {
                    ForEach(viewModel.presetCategories) { cat in
                        HStack {
                            Image(systemName: categoryIcon(cat.name, storedIcon: cat.icon)).font(.title3).foregroundStyle(AppColors.accentPurple).frame(width: 28)
                            Text(cat.name)
                            Spacer()
                            Image(systemName: "lock.fill").foregroundStyle(DS.textSecondary).font(.caption)
                        }
                    }
                }
            }
            Section("Custom categories") {
                if viewModel.customCategories.isEmpty {
                    Text("No custom categories yet").foregroundStyle(DS.textSecondary)
                } else {
                    ForEach(viewModel.customCategories) { cat in
                        HStack {
                            Image(systemName: categoryIcon(cat.name, storedIcon: cat.icon)).font(.title3).foregroundStyle(AppColors.accentPurple).frame(width: 28)
                            Text(cat.name)
                        }
                    }
                    .onDelete { indexSet in
                        let ids = indexSet.map { viewModel.customCategories[$0].id }
                        Task {
                            for id in ids { await viewModel.deleteCategory(id) }
                        }
                    }
                }
            }
        }
        .navigationTitle("Categories")
        .alert("Something went wrong", isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("Close") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if canManage { Button { showAdd = true } label: { Image(systemName: "plus") } }
            }
        }
        .alert("New category", isPresented: $showAdd) {
            TextField("Category name", text: $newName)
            Button("Add") {
                Task { await viewModel.addCategory(name: newName, icon: newIcon); newName = "" }
            }
            Button("Cancel", role: .cancel) { newName = "" }
        }
        .task { await viewModel.load() }
        .onDisappear { viewModel.cleanup() }
    }
}
