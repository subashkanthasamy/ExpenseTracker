import SwiftUI
import Shared

struct SavingsView: View {
    @Bindable var viewModel: SavingsViewModel
    /// Shared household configuration is owner-level. Defaults to false so nothing the
    /// rules would reject is shown before the role resolves.
    var canManage: Bool = false
    @State private var showAdd = false
    @State private var goalName = ""
    @State private var targetStr = ""
    @State private var icon = "🏯"
    @State private var showContribute = false
    @State private var contributeGoalId = ""
    @State private var contributeAmount = ""

    var body: some View {
        Group {
            if viewModel.goals.isEmpty && !viewModel.isLoading {
                VStack(spacing: 12) {
                    Image(systemName: "target").font(.system(size: 48)).foregroundStyle(DS.textSecondary)
                    Text("No savings goals yet").foregroundStyle(DS.textSecondary)
                    Text("Tap + to add a savings goal.").font(.caption).foregroundStyle(DS.textSecondary)
                }
            } else {
                List {
                    ForEach(viewModel.goals) { goal in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(goal.icon).font(.title2)
                                Text(goal.name).bold()
                                Spacer()
                                Text("\(Int(goal.progress * 100))%").foregroundStyle(AppColors.accentPurple)
                            }
                            ProgressView(value: goal.progress).tint(AppColors.savingsGreen)
                            HStack {
                                Text(formatCurrency(goal.currentAmount)).font(.caption)
                                Text("of").font(.caption).foregroundStyle(DS.textSecondary)
                                Text(formatCurrency(goal.targetAmount)).font(.caption)
                                Spacer()
                                Button("Add") {
                                    contributeGoalId = goal.id; showContribute = true
                                }
                                .font(.caption).buttonStyle(.bordered)
                            }
                        }
                        .swipeActions {
                            if canManage {
                                Button(role: .destructive) { Task { await viewModel.deleteGoal(goal.id) } }
                                label: { Label("Delete", systemImage: "trash") }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Savings goals")
        .alert("Something went wrong", isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("Close") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
        .toolbar {
            ToolbarItem(placement: .primaryAction) { if canManage { Button { showAdd = true } label: { Image(systemName: "plus") } } }
        }
        .alert("New savings goal", isPresented: $showAdd) {
            TextField("Goal name", text: $goalName)
            TextField("Amount to save", text: $targetStr)
            Button("Add") {
                let name = goalName, target = targetStr, ic = icon
                goalName = ""; targetStr = ""
                if let t = Double(target) { Task { await viewModel.addGoal(name: name, target: t, icon: ic, targetDate: nil) } }
            }
            Button("Cancel", role: .cancel) { goalName = ""; targetStr = "" }
        }
        .alert("Add contribution", isPresented: $showContribute) {
            TextField("Amount", text: $contributeAmount)
            Button("Add") {
                let amt = contributeAmount, gid = contributeGoalId
                contributeAmount = ""
                if let a = Double(amt) { Task { await viewModel.addContribution(goalId: gid, amount: a) } }
            }
            Button("Cancel", role: .cancel) { contributeAmount = "" }
        }
        .task { await viewModel.load() }
    }
}
