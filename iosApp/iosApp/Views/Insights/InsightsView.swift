import SwiftUI
import Shared

struct InsightsView: View {
    @Bindable var viewModel: InsightsViewModel
    var onPersonSelected: (String) -> Void = { _ in }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Summary Card
                VStack(spacing: 8) {
                    Text("This Month").font(.subheadline).foregroundStyle(.secondary)
                    Text(formatCurrency(viewModel.totalSpent)).font(.title).bold()
                    if viewModel.lastPeriodSpent > 0 {
                        let diff = viewModel.totalSpent - viewModel.lastPeriodSpent
                        let pct = diff / viewModel.lastPeriodSpent * 100
                        HStack {
                            Image(systemName: diff >= 0 ? "arrow.up.right" : "arrow.down.right")
                            Text(String(format: "%.0f%% vs last month", abs(pct)))
                        }
                        .foregroundStyle(diff >= 0 ? .red : .green)
                        .font(.caption)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(.regularMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                // Category Breakdown
                if !viewModel.categoryBreakdown.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("By Category").font(.headline)
                        ForEach(viewModel.categoryBreakdown.sorted(by: { $0.value > $1.value }), id: \.key) { name, amount in
                            HStack {
                                Text(categoryEmoji(name))
                                Text(name)
                                Spacer()
                                Text(formatCurrency(amount)).bold()
                            }
                            let pct = viewModel.totalSpent > 0 ? amount / viewModel.totalSpent : 0
                            ProgressView(value: pct)
                                .tint(AppColors.accentPurple)
                        }
                    }
                    .padding()
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                // Spending split — only meaningful once more than one member has recorded
                // something. Share of total rather than a bare ranked list of amounts: the
                // same numbers as a proportion read as a contribution breakdown rather than
                // a leaderboard.
                if viewModel.personSplit.count > 1 {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("By Person").font(.headline)
                        ForEach(viewModel.personSplit, id: \.userId) { person in
                            Button { onPersonSelected(person.userId) } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(person.name)
                                        Spacer()
                                        Text(formatCurrency(person.amount)).bold()
                                    }
                                    ProgressView(value: min(max(person.share, 0), 1))
                                        .tint(AppColors.accentPurple)
                                    Text("\(Int(person.share * 100))% of total")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding()
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                // Top Category
                if !viewModel.topCategory.isEmpty {
                    HStack {
                        Image(systemName: "star.fill").foregroundStyle(.yellow)
                        Text("Top: \(viewModel.topCategory)")
                        Spacer()
                    }
                    .padding()
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
            }
            .padding()
        }
        .navigationTitle("Insights")
        .task { await viewModel.load() }
    }
}
