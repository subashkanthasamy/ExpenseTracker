import SwiftUI

struct InsightsView: View {
    @Bindable var viewModel: InsightsViewModel

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
