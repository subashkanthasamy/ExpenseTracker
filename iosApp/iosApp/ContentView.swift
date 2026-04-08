import SwiftUI

struct ContentView: View {
    @State private var authService = AuthService()
    @State private var firestoreService = FirestoreService()
    @State private var authVM: AuthViewModel?
    @State private var showSignUp = false

    var body: some View {
        Group {
            if authService.isAuthenticated {
                if authVM?.needsHouseholdSetup == true {
                    HouseholdSetupView(viewModel: authVM!)
                } else {
                    MainTabView(authService: authService, firestoreService: firestoreService, authVM: authVM!)
                }
            } else {
                if showSignUp {
                    SignUpView(viewModel: authVM!, onBack: { showSignUp = false })
                } else {
                    LoginView(viewModel: authVM!, onSignUp: { showSignUp = true })
                }
            }
        }
        .onAppear {
            if authVM == nil {
                authVM = AuthViewModel(authService: authService, firestoreService: firestoreService)
            }
        }
        .onChange(of: authService.isAuthenticated) { _, newValue in
            authVM?.isAuthenticated = newValue
            if newValue {
                Task {
                    if let uid = authService.currentUserId {
                        let households = try? await firestoreService.getUserHouseholds(userId: uid)
                        await MainActor.run { authVM?.needsHouseholdSetup = households?.isEmpty ?? true }
                    }
                }
            }
        }
    }
}

struct MainTabView: View {
    let authService: AuthService
    let firestoreService: FirestoreService
    @Bindable var authVM: AuthViewModel
    @State private var selectedTab = 0
    @State private var showAddExpense = false
    @State private var editExpenseId: String?

    var body: some View {
        TabView(selection: $selectedTab) {
            // Dashboard
            NavigationStack {
                DashboardView(
                    viewModel: DashboardViewModel(authService: authService, firestoreService: firestoreService),
                    onAddExpense: { showAddExpense = true },
                    onExpenseList: { selectedTab = 1 },
                    onSettings: { selectedTab = 4 }
                )
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(0)

            // Expenses
            NavigationStack {
                ExpenseListView(
                    viewModel: ExpenseListViewModel(authService: authService, firestoreService: firestoreService),
                    onAdd: { showAddExpense = true },
                    onEdit: { id in editExpenseId = id; showAddExpense = true }
                )
            }
            .tabItem { Label("Timeline", systemImage: "list.bullet") }
            .tag(1)

            // Insights
            NavigationStack {
                InsightsView(viewModel: InsightsViewModel(authService: authService, firestoreService: firestoreService))
            }
            .tabItem { Label("Insights", systemImage: "chart.pie.fill") }
            .tag(2)

            // Net Worth
            NavigationStack {
                NetWorthView(viewModel: NetWorthViewModel(authService: authService, firestoreService: firestoreService))
            }
            .tabItem { Label("Wealth", systemImage: "banknote.fill") }
            .tag(3)

            // More
            NavigationStack {
                MoreView(authService: authService, firestoreService: firestoreService, authVM: authVM)
            }
            .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
            .tag(4)
        }
        .tint(AppColors.accentPurple)
        .sheet(isPresented: $showAddExpense) {
            AddEditExpenseView(viewModel: AddEditExpenseViewModel(
                authService: authService, firestoreService: firestoreService, expenseId: editExpenseId
            ))
        }
        .onChange(of: showAddExpense) { _, newValue in
            if !newValue { editExpenseId = nil }
        }
    }
}

struct MoreView: View {
    let authService: AuthService
    let firestoreService: FirestoreService
    @Bindable var authVM: AuthViewModel

    var body: some View {
        List {
            NavigationLink {
                BudgetView(viewModel: BudgetViewModel(authService: authService, firestoreService: firestoreService))
            } label: { Label("Budgets", systemImage: "chart.pie") }

            NavigationLink {
                CategoryView(viewModel: CategoryViewModel(authService: authService, firestoreService: firestoreService))
            } label: { Label("Categories", systemImage: "tag.fill") }

            NavigationLink {
                SavingsView(viewModel: SavingsViewModel(authService: authService, firestoreService: firestoreService))
            } label: { Label("Savings Goals", systemImage: "target") }

            NavigationLink {
                HouseholdView(viewModel: HouseholdViewModel(authService: authService, firestoreService: firestoreService))
            } label: { Label("Household", systemImage: "house.fill") }

            NavigationLink {
                FinancialCoachView(viewModel: FinancialCoachViewModel(authService: authService, firestoreService: firestoreService))
            } label: { Label("Financial Coach", systemImage: "brain.head.profile") }

            Section {
                SettingsView(user: authService.currentUser, onSignOut: { authVM.signOut() },
                             onHousehold: {}, onCategories: {})
            }
        }
        .navigationTitle("More")
    }
}
