import SwiftUI
import Shared

struct CategoryView: View {
    @Bindable var viewModel: CategoryViewModel
    @State private var showAdd = false
    @State private var newName = ""
    @State private var newIcon = "💳"

    var body: some View {
        List {
            if !viewModel.presetCategories.isEmpty {
                Section("Preset Categories") {
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
            Section("Custom Categories") {
                if viewModel.customCategories.isEmpty {
                    Text("No custom categories").foregroundStyle(DS.textSecondary)
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
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showAdd = true } label: { Image(systemName: "plus") }
            }
        }
        .alert("New Category", isPresented: $showAdd) {
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
