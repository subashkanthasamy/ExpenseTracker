import SwiftUI
import Shared

struct ContentView: View {
    @State private var authService = AuthService()
    @State private var firestoreService = FirestoreService()
    @State private var authVM: AuthViewModel?
    @State private var showSignUp = false
    @State private var prefs = AppPreferences()
    @State private var notifications = NotificationService()
    @State private var biometricUnlocked = false
    /// Safety net: never let the splash hang if Firebase's auth callback never arrives.
    @State private var authResolveTimedOut = false

    var body: some View {
        Group {
            if prefs.biometricEnabled && !biometricUnlocked {
                BiometricLockView { biometricUnlocked = true }
            } else {
                content
            }
        }
        .preferredColorScheme(prefs.colorScheme)
    }

    private var content: some View {
        Group {
            if let vm = authVM {
                if !authService.hasResolvedInitialState && !vm.isAuthenticated && !authResolveTimedOut {
                    // Firebase restores a persisted session asynchronously. Showing the
                    // sign-in screen during that window made Login flash on every launch
                    // for an already-signed-in user.
                    LaunchSplashView()
                        .task {
                            try? await Task.sleep(for: .seconds(5))
                            authResolveTimedOut = true
                        }
                } else if vm.isAuthenticated || authService.isAuthenticated {
                    if vm.needsHouseholdSetup {
                        HouseholdSetupView(viewModel: vm)
                    } else {
                        MainTabView(authService: authService, firestoreService: firestoreService, authVM: vm, prefs: prefs, notifications: notifications)
                    }
                } else {
                    if showSignUp {
                        SignUpView(viewModel: vm, onBack: { showSignUp = false })
                    } else {
                        LoginView(viewModel: vm, onSignUp: { showSignUp = true })
                    }
                }
            } else {
                LaunchSplashView()
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
    @Bindable var prefs: AppPreferences
    @Bindable var notifications: NotificationService
    @State private var selectedTab = 0
    @State private var showAddExpense = false
    @State private var editExpenseId: String?

    // Store ViewModels as @State so they persist across re-renders
    @State private var dashboardVM: DashboardViewModel?
    @State private var expenseListVM: ExpenseListViewModel?
    @State private var insightsVM: InsightsViewModel?
    @State private var netWorthVM: NetWorthViewModel?

    /// Owned here so every tab reads the same role rather than resolving it five times.
    @State private var sessionRole: SessionRole?

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                if let vm = dashboardVM {
                    DashboardView(
                        viewModel: vm,
                        canAddExpense: sessionRole?.canAddExpense ?? false,
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
                        sessionRole: sessionRole,
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
                    InsightsView(viewModel: vm) { uid in
                        // Both view models are owned here, so the split can hand the filter
                        // straight to the expense list and switch to its tab.
                        expenseListVM?.personFilter = uid
                        selectedTab = 1
                    }
                } else {
                    ProgressView()
                }
            }
            .tabItem { Label("Insights", systemImage: "chart.pie.fill") }
            .tag(2)

            NavigationStack {
                if let vm = netWorthVM {
                    NetWorthView(viewModel: vm, canManage: sessionRole?.canManageSharedConfig ?? false)
                } else {
                    ProgressView()
                }
            }
            .tabItem { Label("Wealth", systemImage: "banknote.fill") }
            .tag(3)

            NavigationStack {
                MoreView(authService: authService, firestoreService: firestoreService, authVM: authVM, prefs: prefs, notifications: notifications, canManageSharedConfig: sessionRole?.canManageSharedConfig ?? false)
            }
            .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
            .tag(4)
        }
        .tint(AppColors.accentPurple)
        .task {
            // Create ViewModels once
            let roles = SessionRole(authService: authService, firestoreService: firestoreService)
            await roles.refresh()
            sessionRole = roles

            let dashVM = DashboardViewModel(authService: authService, firestoreService: firestoreService)
            dashVM.canReadAllExpenses = roles.canReadAllExpenses
            dashboardVM = dashVM
            let listVM = ExpenseListViewModel(authService: authService, firestoreService: firestoreService)
            // Set before the list's .task fires, or it would subscribe with the restricted
            // query shape and a manager would silently miss other members' personal rows.
            listVM.canReadAllExpenses = roles.canReadAllExpenses
            expenseListVM = listVM
            insightsVM = InsightsViewModel(authService: authService, firestoreService: firestoreService)
            netWorthVM = NetWorthViewModel(authService: authService, firestoreService: firestoreService)

            // Recurring rules are generated with catch-up on launch rather than from a
            // background task — BGTaskScheduler is best-effort and may not run for days.
            if let hid = await authService.getActiveHouseholdId() {
                let created = try? await RecurringExpenseService(firestoreService: firestoreService)
                    .generateDueExpenses(householdId: hid)
                if let created, created > 0 {
                    print("Recurring: created \(created) due expense(s)")
                    await dashboardVM?.load()
                }
            }
            await notifications.rescheduleAll()
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
    @Bindable var prefs: AppPreferences
    @Bindable var notifications: NotificationService
    /// Budgets, categories, savings goals and recurring rules are owner-level.
    var canManageSharedConfig: Bool = false

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
                    BudgetView(viewModel: BudgetViewModel(authService: authService, firestoreService: firestoreService), canManage: canManageSharedConfig)
                } label: { Label("Budgets", systemImage: "chart.pie") }

                NavigationLink {
                    CategoryView(viewModel: CategoryViewModel(authService: authService, firestoreService: firestoreService), canManage: canManageSharedConfig)
                } label: { Label("Categories", systemImage: "tag.fill") }

                NavigationLink {
                    SavingsView(viewModel: SavingsViewModel(authService: authService, firestoreService: firestoreService), canManage: canManageSharedConfig)
                } label: { Label("Savings Goals", systemImage: "target") }

                NavigationLink {
                    RecurringView(viewModel: RecurringViewModel(authService: authService, firestoreService: firestoreService), canManage: canManageSharedConfig)
                } label: { Label("Recurring Expenses", systemImage: "arrow.clockwise.circle") }

                NavigationLink {
                    ReminderView(notifications: notifications)
                } label: { Label("Reminders", systemImage: "bell.badge") }

                NavigationLink {
                    FinancialCoachView(viewModel: FinancialCoachViewModel(authService: authService, firestoreService: firestoreService))
                } label: { Label("Financial Coach", systemImage: "brain.head.profile") }
            }

            Section("App") {
                NavigationLink {
                    SettingsView(
                        viewModel: SettingsViewModel(authService: authService, firestoreService: firestoreService),
                        prefs: prefs
                    )
                } label: { Label("Settings", systemImage: "gearshape.fill") }
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
