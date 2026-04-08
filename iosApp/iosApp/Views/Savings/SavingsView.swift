import SwiftUI

struct SavingsView: View {
    @Bindable var viewModel: SavingsViewModel
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
                    Image(systemName: "target").font(.system(size: 48)).foregroundStyle(.secondary)
                    Text("No savings goals yet").foregroundStyle(.secondary)
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
                                Text("of").font(.caption).foregroundStyle(.secondary)
                                Text(formatCurrency(goal.targetAmount)).font(.caption)
                                Spacer()
                                Button("Add") {
                                    contributeGoalId = goal.id; showContribute = true
                                }
                                .font(.caption).buttonStyle(.bordered)
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) { Task { await viewModel.deleteGoal(goal.id) } }
                            label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                }
            }
        }
        .navigationTitle("Savings Goals")
        .toolbar {
            ToolbarItem(placement: .primaryAction) { Button { showAdd = true } label: { Image(systemName: "plus") } }
        }
        .alert("New Goal", isPresented: $showAdd) {
            TextField("Goal name", text: $goalName)
            TextField("Target amount", text: $targetStr)
            Button("Add") {
                if let t = Double(targetStr) { Task { await viewModel.addGoal(name: goalName, target: t, icon: icon, targetDate: nil) } }
                goalName = ""; targetStr = ""
            }
            Button("Cancel", role: .cancel) { goalName = ""; targetStr = "" }
        }
        .alert("Add Contribution", isPresented: $showContribute) {
            TextField("Amount", text: $contributeAmount)
            Button("Add") {
                if let a = Double(contributeAmount) { Task { await viewModel.addContribution(goalId: contributeGoalId, amount: a) } }
                contributeAmount = ""
            }
            Button("Cancel", role: .cancel) { contributeAmount = "" }
        }
        .task { await viewModel.load() }
    }
}
