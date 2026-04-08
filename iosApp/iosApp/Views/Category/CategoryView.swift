import SwiftUI

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
                            Text(categoryEmoji(cat.name)).font(.title3)
                            Text(cat.name)
                            Spacer()
                            Image(systemName: "lock.fill").foregroundStyle(.secondary).font(.caption)
                        }
                    }
                }
            }
            Section("Custom Categories") {
                if viewModel.customCategories.isEmpty {
                    Text("No custom categories").foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.customCategories) { cat in
                        HStack {
                            Text(categoryEmoji(cat.name)).font(.title3)
                            Text(cat.name)
                        }
                    }
                    .onDelete { indexSet in
                        for i in indexSet {
                            Task { await viewModel.deleteCategory(viewModel.customCategories[i].id) }
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
        .onAppear { viewModel.load() }
        .onDisappear { viewModel.cleanup() }
    }
}
