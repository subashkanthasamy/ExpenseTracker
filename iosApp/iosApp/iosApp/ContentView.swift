import SwiftUI

struct ContentView: View {
    @State private var authService = AuthService()
    @State private var firestoreService = FirestoreService()
    @State private var authVM: AuthViewModel?
    @State private var showSignUp = false

    var body: some View {
        Group {
            if let vm = authVM {
                if vm.isAuthenticated || authService.isAuthenticated {
                    if vm.needsHouseholdSetup {
                        HouseholdSetupView(viewModel: vm)
                    } else {
                        MainTabView(authService: authService, firestoreService: firestoreService, authVM: vm)
                    }
                } else {
                    if showSignUp {
                        SignUpView(viewModel: vm, onBack: { showSignUp = false })
                    } else {
                        LoginView(viewModel: vm, onSignUp: { showSignUp = true })
                    }
                }
            } else {
                ProgressView("Loading...")
                    .task {
                        authVM = AuthViewModel(authService: authService, firestoreService: firestoreService)
                    }
            }
        }
        .onChange(of: authService.isAuthenticated) { _, newValue in
            guard let vm = authVM else { return }
            vm.isAuthenticated = newValue
            if newValue {
                Task {
                    if let uid = authService.currentUserId {
                        let households = (try? await firestoreService.getUserHouseholds(userId: uid)) ?? []
                        vm.needsHouseholdSetup = households.isEmpty
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

    // Store ViewModels as @State so they persist across re-renders
    @State private var dashboardVM: DashboardViewModel?
    @State private var expenseListVM: ExpenseListViewModel?
    @State private var insightsVM: InsightsViewModel?
    @State private var netWorthVM: NetWorthViewModel?

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                if let vm = dashboardVM {
                    DashboardView(
                        viewModel: vm,
                        onAddExpense: { showAddExpense = true },
                        onExpenseList: { selectedTab = 1 },
                        onSettings: { selectedTab = 4 }
                    )
                } else {
                    ProgressView()
                }
            }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(0)

            NavigationStack {
                if let vm = expenseListVM {
                    ExpenseListView(
                        viewModel: vm,
                        onAdd: { showAddExpense = true },
                        onEdit: { id in editExpenseId = id; showAddExpense = true }
                    )
                } else {
                    ProgressView()
                }
            }
            .tabItem { Label("Timeline", systemImage: "list.bullet") }
            .tag(1)

            NavigationStack {
                if let vm = insightsVM {
                    InsightsView(viewModel: vm)
                } else {
                    ProgressView()
                }
            }
            .tabItem { Label("Insights", systemImage: "chart.pie.fill") }
            .tag(2)

            NavigationStack {
                if let vm = netWorthVM {
                    NetWorthView(viewModel: vm)
                } else {
                    ProgressView()
                }
            }
            .tabItem { Label("Wealth", systemImage: "banknote.fill") }
            .tag(3)

            NavigationStack {
                MoreView(authService: authService, firestoreService: firestoreService, authVM: authVM)
            }
            .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
            .tag(4)
        }
        .tint(AppColors.accentPurple)
        .task {
            // Create ViewModels once
            dashboardVM = DashboardViewModel(authService: authService, firestoreService: firestoreService)
            expenseListVM = ExpenseListViewModel(authService: authService, firestoreService: firestoreService)
            insightsVM = InsightsViewModel(authService: authService, firestoreService: firestoreService)
            netWorthVM = NetWorthViewModel(authService: authService, firestoreService: firestoreService)
        }
        .sheet(isPresented: $showAddExpense) {
            AddEditExpenseView(viewModel: AddEditExpenseViewModel(
                authService: authService, firestoreService: firestoreService, expenseId: editExpenseId
            ))
        }
        .onChange(of: showAddExpense) { _, newValue in
            // When sheet closes after adding expense, refresh insights
            if !newValue {
                editExpenseId = nil
                Task { await insightsVM?.load() }
            }
        }
    }
}

struct MoreView: View {
    let authService: AuthService
    let firestoreService: FirestoreService
    @Bindable var authVM: AuthViewModel

    var body: some View {
        List {
            Section("Account") {
                if let user = authService.currentUser {
                    LabeledContent("Name", value: user.displayName)
                    LabeledContent("Email", value: user.email)
                }
            }

            Section("Features") {
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
                    FinancialCoachView(viewModel: FinancialCoachViewModel(authService: authService, firestoreService: firestoreService))
                } label: { Label("Financial Coach", systemImage: "brain.head.profile") }
            }

            Section("Household") {
                NavigationLink {
                    HouseholdView(viewModel: HouseholdViewModel(authService: authService, firestoreService: firestoreService))
                } label: { Label("Manage Household", systemImage: "house.fill") }
            }

            Section {
                Button("Sign Out", role: .destructive) {
                    authVM.signOut()
                }
            }

            Section {
                LabeledContent("Version", value: "1.0.0")
            }
        }
        .navigationTitle("More")
    }
}
