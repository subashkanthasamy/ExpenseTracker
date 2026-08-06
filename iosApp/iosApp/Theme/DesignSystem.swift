import SwiftUI

/// Tokens and components for the dark design in `docs/Expense Tracker/*.pdf`.
///
/// The designs are dark-only — there is no light variant in the source files — so screens
/// built against these tokens pin themselves to `.dark` rather than following the app theme.
/// See the note in AddEditExpenseView.
enum DS {

    // MARK: - Surfaces

    /// Screen canvas. Darker than AppColors.backgroundDark, matching the mockups.
    static let canvas = Color(hex: 0xFF0B0B12)
    /// Default card / field background.
    static let card = Color(hex: 0xFF16161E)
    /// Chips, category tiles, anything sitting on top of a card.
    static let elevated = Color(hex: 0xFF1E1E28)
    /// Header bar behind the title row.
    static let header = Color(hex: 0xFF12121A)
    /// Hairline borders and dashed outlines.
    static let stroke = Color(hex: 0xFF2A2A36)

    // MARK: - Text

    static let textPrimary = Color.white
    static let textSecondary = Color(hex: 0xFF9A9AAC)
    /// Small-caps section labels ("CATEGORY", "AMOUNT ₹").
    static let textLabel = Color(hex: 0xFF7A7A8C)

    // MARK: - Accents

    static let accent = AppColors.accentPurple            // #7B61FF, already the brand purple
    static let accentSoft = Color(hex: 0xFF9C8BFF)
    static let expense = Color(hex: 0xFFE5484D)
    static let income = Color(hex: 0xFF4ADE80)

    /// Primary call-to-action gradient, left to right as in the mockups.
    static let ctaGradient = LinearGradient(
        colors: [Color(hex: 0xFF6D5BFF), Color(hex: 0xFF9C4DFF)],
        startPoint: .leading,
        endPoint: .trailing
    )

    // MARK: - Metrics

    static let cardRadius: CGFloat = 16
    static let tileRadius: CGFloat = 14
    static let screenPadding: CGFloat = 20
}

// MARK: - Building blocks

/// Uppercase, letter-spaced section label used throughout the designs.
struct DSSectionLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .semibold))
            .tracking(1.2)
            .foregroundStyle(DS.textLabel)
    }
}

/// Rounded dark container.
struct DSCard<Content: View>: View {
    var padding: CGFloat = 16
    var background: Color = DS.card
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: DS.cardRadius, style: .continuous))
    }
}

/// Expense / Income selector — filled in the active colour, plain when inactive.
struct DSTypeToggle: View {
    @Binding var isExpense: Bool

    var body: some View {
        HStack(spacing: 0) {
            segment(title: "Expense", icon: "arrow.up", active: isExpense, tint: DS.expense) {
                isExpense = true
            }
            segment(title: "Income", icon: "arrow.down", active: !isExpense, tint: DS.income) {
                isExpense = false
            }
        }
        .background(DS.card)
        .clipShape(RoundedRectangle(cornerRadius: DS.cardRadius, style: .continuous))
    }

    private func segment(
        title: String,
        icon: String,
        active: Bool,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.system(size: 15, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .foregroundStyle(active ? Color.white : DS.textSecondary)
            .background(active ? tint : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: DS.cardRadius, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// A capture shortcut row — the mic and receipt entry points in the Add Transaction design.
struct DSCaptureRow: View {
    let icon: String
    let title: String
    var dashed: Bool = false
    var centered: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if centered {
                    VStack(spacing: 8) {
                        Image(systemName: icon).font(.system(size: 20))
                        Text(title).font(.system(size: 15, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 22)
                } else {
                    HStack(spacing: 14) {
                        Image(systemName: icon)
                            .font(.system(size: 16))
                            .frame(width: 40, height: 40)
                            .background(DS.elevated)
                            .clipShape(Circle())
                        Text(title).font(.system(size: 15, weight: .medium))
                        Spacer()
                    }
                    .padding(16)
                }
            }
            .foregroundStyle(centered ? DS.accentSoft : DS.textPrimary)
            .frame(maxWidth: .infinity)
            .background(DS.card)
            .clipShape(RoundedRectangle(cornerRadius: DS.cardRadius, style: .continuous))
            .overlay {
                if dashed {
                    RoundedRectangle(cornerRadius: DS.cardRadius, style: .continuous)
                        .strokeBorder(DS.accent.opacity(0.45), style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                }
            }
        }
        .buttonStyle(.plain)
    }
}

/// Emoji category tile; selected state gets a purple ring, as in the design.
struct DSCategoryTile: View {
    let emoji: String
    let name: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Text(emoji).font(.system(size: 24))
                Text(name)
                    .font(.system(size: 11, weight: .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .foregroundStyle(isSelected ? DS.textPrimary : DS.textSecondary)
            .background(isSelected ? DS.accent.opacity(0.16) : DS.elevated)
            .clipShape(RoundedRectangle(cornerRadius: DS.tileRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: DS.tileRadius, style: .continuous)
                    .strokeBorder(isSelected ? DS.accent : .clear, lineWidth: 1.5)
            }
        }
        .buttonStyle(.plain)
    }
}

/// Full-width gradient call to action.
struct DSPrimaryButton: View {
    let title: String
    var systemImage: String? = "checkmark.circle.fill"
    var isBusy: Bool = false
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if isBusy {
                    ProgressView().tint(.white)
                } else {
                    Text(title).font(.system(size: 17, weight: .semibold))
                    if let systemImage {
                        Image(systemName: systemImage).font(.system(size: 16, weight: .semibold))
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .foregroundStyle(.white)
            .background(DS.ctaGradient)
            .clipShape(RoundedRectangle(cornerRadius: DS.cardRadius, style: .continuous))
            .opacity(isEnabled && !isBusy ? 1 : 0.45)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled || isBusy)
    }
}
