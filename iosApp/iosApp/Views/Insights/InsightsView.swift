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
                    Text("Shared this month").font(.subheadline).foregroundStyle(DS.textSecondary)
                    Text(formatCurrency(viewModel.totalSpent)).font(.title).bold()
                    Text("Personal expenses are not counted here")
                        .font(.caption2)
                        .foregroundStyle(DS.textSecondary)
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
                .background(DS.card, in: RoundedRectangle(cornerRadius: DS.cardRadius))

                // Category Breakdown
                if !viewModel.categoryBreakdown.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("By Category").font(.headline)
                        ForEach(viewModel.categoryBreakdown.sorted(by: { $0.value > $1.value }), id: \.key) { name, amount in
                            HStack {
                                Image(systemName: categoryIcon(name)).foregroundStyle(AppColors.accentPurple).frame(width: 22)
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
                    .background(DS.card, in: RoundedRectangle(cornerRadius: DS.cardRadius))
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
                                        .foregroundStyle(DS.textSecondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding()
                    .background(DS.card, in: RoundedRectangle(cornerRadius: DS.cardRadius))
                }

                // Only meaningful once more than one instrument has been used.
                if viewModel.paymentSplit.count > 1 {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("By Payment Method").font(.headline)
                        ForEach(viewModel.paymentSplit, id: \.id) { slice in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(slice.label)
                                    Spacer()
                                    Text(formatCurrency(slice.amount)).bold()
                                }
                                ProgressView(value: min(max(slice.share, 0), 1))
                                    .tint(AppColors.accentPurple)
                            }
                        }
                    }
                    .padding()
                    .background(DS.card, in: RoundedRectangle(cornerRadius: DS.cardRadius))
                }

                // Top Category
                if !viewModel.topCategory.isEmpty {
                    HStack {
                        Image(systemName: "star.fill").foregroundStyle(.yellow)
                        Text("Top: \(viewModel.topCategory)")
                        Spacer()
                    }
                    .padding()
                    .background(DS.card, in: RoundedRectangle(cornerRadius: DS.cardRadius))
                }
            }
            .padding()
        }
        .background(DS.canvas)
        .navigationTitle("Insights")
        .task { await viewModel.load() }
    }
}
