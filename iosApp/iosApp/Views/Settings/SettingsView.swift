import SwiftUI

struct SettingsView: View {
    let user: AppUser?
    var onSignOut: () -> Void
    var onHousehold: () -> Void
    var onCategories: () -> Void

    var body: some View {
        List {
            Section("Profile") {
                if let user = user {
                    LabeledContent("Name", value: user.displayName)
                    LabeledContent("Email", value: user.email)
                }
            }

            Section("Manage") {
                Button(action: onHousehold) {
                    Label("Household", systemImage: "house.fill")
                }
                Button(action: onCategories) {
                    Label("Categories", systemImage: "tag.fill")
                }
            }

            Section {
                Button("Sign Out", role: .destructive, action: onSignOut)
            }
        }
        .navigationTitle("Settings")
    }
}
