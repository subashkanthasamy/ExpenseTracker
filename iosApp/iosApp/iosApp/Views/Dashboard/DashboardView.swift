import SwiftUI

struct DashboardView: View {
    @Bindable var viewModel: DashboardViewModel
    var onAddExpense: () -> Void
    var onExpenseList: () -> Void
    var onSettings: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading) {
                        Text("Hello! 👋").font(.title2).bold()
                        Text(Date(), format: .dateTime.month(.wide).year())
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(action: onSettings) {
                        Image(systemName: "gearshape.fill")
                            .font(.title3).foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal)

                // Balance Card
                VStack(spacing: 12) {
                    Text("This Month").font(.subheadline).foregroundStyle(.white.opacity(0.8))
                    Text(formatCurrency(viewModel.monthTotal))
                        .font(.system(size: 36, weight: .bold))
                        .foregroundStyle(.white)
                    HStack(spacing: 20) {
                        let diff = viewModel.monthTotal - viewModel.lastMonthTotal
                        let pct = viewModel.lastMonthTotal > 0 ? (diff / viewModel.lastMonthTotal * 100) : 0
                        Label(String(format: "%.0f%%", abs(pct)), systemImage: diff >= 0 ? "arrow.up.right" : "arrow.down.right")
                            .foregroundStyle(diff >= 0 ? .red.opacity(0.9) : .green.opacity(0.9))
                            .font(.caption).bold()
                        Text("vs last month").font(.caption).foregroundStyle(.white.opacity(0.7))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(24)
                .background(
                    LinearGradient(colors: [AppColors.gradientPurple, AppColors.gradientPink],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .padding(.horizontal)

                // Category Breakdown
                if !viewModel.categoryBreakdown.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Categories").font(.headline)
                            Spacer()
                        }
                        ForEach(viewModel.categoryBreakdown) { cat in
                            HStack {
                                Text(categoryEmoji(cat.categoryName))
                                Text(cat.categoryName).font(.subheadline)
                                Spacer()
                                Text(formatCurrency(cat.amount)).font(.subheadline).bold()
                            }
                            ProgressView(value: Double(cat.percentage), total: 100)
                                .tint(cat.color)
                        }
                    }
                    .padding()
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal)
                }

                // Recent Transactions
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Recent").font(.headline)
                        Spacer()
                        Button("See All", action: onExpenseList)
                            .font(.caption).foregroundStyle(AppColors.accentPurple)
                    }
                    if viewModel.recentExpenses.isEmpty {
                        Text("No expenses yet").foregroundStyle(.secondary).padding()
                    } else {
                        ForEach(viewModel.recentExpenses) { expense in
                            ExpenseRow(expense: expense)
                        }
                    }
                }
                .padding()
                .background(.regularMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .task { await viewModel.load() }
        .onDisappear { viewModel.cleanup() }
        .overlay(alignment: .bottomTrailing) {
            Button(action: onAddExpense) {
                Image(systemName: "plus")
                    .font(.title2).bold()
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(AppColors.accentPurple)
                    .clipShape(Circle())
                    .shadow(radius: 4)
            }
            .padding(24)
        }
    }
}

struct ExpenseRow: View {
    let expense: Expense
    var body: some View {
        HStack {
            Text(categoryEmoji(expense.categoryName))
                .font(.title2)
                .frame(width: 44, height: 44)
                .background(Color.purple.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading) {
                Text(expense.categoryName).font(.subheadline).bold()
                Text(expense.notes.isEmpty ? expense.addedByName : expense.notes)
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text(formatCurrency(expense.amount)).font(.subheadline).bold()
                Text(expense.date, format: .dateTime.day().month(.abbreviated))
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}
