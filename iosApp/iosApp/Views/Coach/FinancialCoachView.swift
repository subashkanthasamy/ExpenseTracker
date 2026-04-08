import SwiftUI

struct FinancialCoachView: View {
    @Bindable var viewModel: FinancialCoachViewModel

    var body: some View {
        VStack(spacing: 0) {
            // Score badge
            HStack {
                Spacer()
                Text("Score: \(viewModel.financialScore)")
                    .font(.caption).bold()
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(AppColors.savingsGreen).foregroundStyle(.white)
                    .clipShape(Capsule())
            }
            .padding()

            // Messages
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(viewModel.messages) { msg in
                            HStack {
                                if msg.isUser { Spacer() }
                                VStack(alignment: msg.isUser ? .trailing : .leading, spacing: 4) {
                                    Text(msg.text)
                                        .padding(12)
                                        .background(msg.isUser ? AppColors.accentPurple : Color(.systemGray5))
                                        .foregroundStyle(msg.isUser ? .white : .primary)
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                    if let stats = msg.inlineStats {
                                        ForEach(stats, id: \.label) { stat in
                                            HStack {
                                                Text(stat.emoji)
                                                Text(stat.label).font(.caption)
                                                Text(stat.value).font(.caption).bold()
                                            }
                                        }
                                    }
                                }
                                .frame(maxWidth: 280, alignment: msg.isUser ? .trailing : .leading)
                                if !msg.isUser { Spacer() }
                            }
                            .id(msg.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: viewModel.messages.count) {
                    if let last = viewModel.messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            // Suggestions
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(["Can I save?", "My score", "Top spending"], id: \.self) { suggestion in
                        Button(suggestion) {
                            viewModel.inputText = suggestion
                            viewModel.sendMessage()
                        }
                        .font(.caption).padding(.horizontal, 12).padding(.vertical, 8)
                        .background(AppColors.accentPurple.opacity(0.1))
                        .clipShape(Capsule())
                    }
                }
                .padding(.horizontal)
            }

            // Input
            HStack {
                TextField("Ask your coach...", text: $viewModel.inputText)
                    .textFieldStyle(.roundedBorder)
                Button { viewModel.sendMessage() } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(AppColors.accentPurple)
                }
                .disabled(viewModel.inputText.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding()
        }
        .navigationTitle("Financial Coach")
        .task { await viewModel.loadContext() }
    }
}
