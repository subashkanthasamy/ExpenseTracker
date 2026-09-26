import SwiftUI
import Shared

struct HouseholdView: View {
    @Bindable var viewModel: HouseholdViewModel
    @State private var showDelete = false

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Loading household…")
            } else if let h = viewModel.household {
                List {
                    // Your role. Without this, a member finds the management controls simply
                    // absent and reads it as the app being broken rather than as a permission.
                    if viewModel.role != HouseholdRole.none {
                        Section {
                            HStack(alignment: .firstTextBaseline, spacing: 10) {
                                Text(Permissions.shared.label(role: viewModel.role))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(DS.accent)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 4)
                                    .background(DS.accent.opacity(0.15), in: Capsule())
                                Text(Permissions.shared.description(role: viewModel.role))
                                    .font(.footnote)
                                    .foregroundStyle(DS.textSecondary)
                            }
                        }
                    }

                    Section("Active household") {
                        LabeledContent("Name", value: h.name)
                        LabeledContent("Invite code") {
                            HStack {
                                Text(h.inviteCode).font(.system(.body, design: .monospaced)).bold()
                                Button {
                                    UIPasteboard.general.string = h.inviteCode
                                } label: {
                                    Image(systemName: "doc.on.doc").font(.caption)
                                }
                                .accessibilityLabel("Copy invite code")
                            }
                        }
                        LabeledContent("Created", value: h.createdAtValue.formatted(date: .abbreviated, time: .omitted))
                    }

                    Section("Members (\(viewModel.members.count))") {
                        ForEach(viewModel.members) { member in
                            MemberRow(
                                member: member,
                                household: h,
                                // Nobody may change the owner's role, including the owner: the
                                // rules pin ownerUid, so the control would only fail.
                                canManage: Permissions.shared.canManageMembers(role: viewModel.role)
                                    && member.uid != h.ownerUid,
                                onSetRole: { role in
                                    Task { await viewModel.setMemberRole(uid: member.uid, role: role) }
                                },
                                onRemove: {
                                    Task { await viewModel.removeMember(uid: member.uid) }
                                }
                            )
                        }
                    }

                    if viewModel.households.count > 1 {
                        Section("Your households") {
                            ForEach(viewModel.households) { household in
                                HStack {
                                    Text(household.name)
                                    Spacer()
                                    if household.id == h.id {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(AppColors.accentPurple)
                                    }
                                }
                            }
                        }
                    }

                    // Creator (or an admin) only. The rules deny it for anyone else, so
                    // showing the button would just produce a failure.
                    if Permissions.shared.canDeleteHousehold(role: viewModel.role) {
                        Section {
                            Button("Delete household", role: .destructive) { showDelete = true }
                        }
                    }
                }
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "house.slash").font(.system(size: 48)).foregroundStyle(DS.textSecondary)
                    Text("No household found").foregroundStyle(DS.textSecondary)
                }
            }
        }
        .navigationTitle("Household")
        .alert("Delete this household?", isPresented: $showDelete) {
            Button("Delete", role: .destructive) { Task { await viewModel.deleteHousehold() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes the household and everything in it — expenses, categories, budgets, savings goals, recurring expenses, assets and liabilities — for every member. This can't be undone.")
        }
        .alert("Something went wrong", isPresented: Binding(
            get: { viewModel.error != nil },
            set: { if !$0 { viewModel.error = nil } }
        )) {
            Button("Close") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
        .task { await viewModel.load() }
    }
}


/// One household member, with the owner's controls when the caller has them.
///
/// Role and removal sit behind a context menu rather than inline buttons: ejecting someone is
/// destructive and shouldn't be one stray tap away in a list.
private struct MemberRow: View {
    let member: AppUser
    let household: Household
    let canManage: Bool
    let onSetRole: (String) -> Void
    let onRemove: () -> Void

    @State private var confirmRemove = false

    private var isOwner: Bool { member.uid == household.ownerUid }
    /// Raw `roles` value: nil for a pre-roles member, otherwise member/admin/guest.
    private var memberRole: String? { household.roles[member.uid] }

    private var roleLabel: String {
        // The owner has no `roles` entry — they are identified by ownerUid — so their label
        // comes from that, not the map.
        if isOwner { return Permissions.shared.label(role: HouseholdRole.owner) }
        switch memberRole {
        case Permissions.shared.ROLE_ADMIN: return Permissions.shared.label(role: HouseholdRole.admin)
        case Permissions.shared.ROLE_GUEST: return Permissions.shared.label(role: HouseholdRole.guest)
        default: return Permissions.shared.label(role: HouseholdRole.member)
        }
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(member.displayName)
                Text(member.email).font(.caption).foregroundStyle(DS.textSecondary)
            }
            Spacer()
            Text(roleLabel)
                .font(.caption.weight(.semibold))
                .foregroundStyle(DS.accent)
            if canManage {
                Menu {
                    // Admin is a co-manager: everything the owner can do except delete the
                    // household.
                    if memberRole != Permissions.shared.ROLE_ADMIN {
                        Button("Make admin") { onSetRole(Permissions.shared.ROLE_ADMIN) }
                    }
                    if memberRole != Permissions.shared.ROLE_MEMBER {
                        Button("Make member") { onSetRole(Permissions.shared.ROLE_MEMBER) }
                    }
                    if memberRole != Permissions.shared.ROLE_GUEST {
                        Button("Make guest") { onSetRole(Permissions.shared.ROLE_GUEST) }
                    }
                    Button("Remove from household", role: .destructive) { confirmRemove = true }
                } label: {
                    Image(systemName: "ellipsis.circle").foregroundStyle(DS.textSecondary)
                }
            }
        }
        .alert("Remove \(member.displayName) from household?", isPresented: $confirmRemove) {
            Button("Remove from household", role: .destructive) { onRemove() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("They lose access to this household right away. Expenses they've already added stay, and they can rejoin with an invite code.")
        }
    }
}
