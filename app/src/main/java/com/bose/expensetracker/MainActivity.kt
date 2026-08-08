package com.bose.expensetracker

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bose.expensetracker.data.preferences.BiometricPreferences
import com.bose.expensetracker.data.preferences.SandboxPreferences
import com.bose.expensetracker.data.preferences.ThemePreferences
import com.bose.expensetracker.data.sync.LocalToFirestoreMigration
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.ui.navigation.AddEditExpenseRoute
import com.bose.expensetracker.ui.navigation.BottomNavBar
import com.bose.expensetracker.ui.navigation.bottomNavItems
import com.bose.expensetracker.ui.navigation.DashboardRoute
import com.bose.expensetracker.ui.navigation.ExpenseTrackerNavGraph
import com.bose.expensetracker.ui.navigation.HouseholdSetupRoute
import com.bose.expensetracker.ui.navigation.LoginRoute
import com.bose.expensetracker.ui.navigation.NotificationsRoute
import com.bose.expensetracker.ui.screen.auth.BiometricHelper
import com.bose.expensetracker.ui.theme.ExpenseTrackerTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var biometricPreferences: BiometricPreferences

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var householdRepository: HouseholdRepository

    @Inject
    lateinit var sandboxPreferences: SandboxPreferences

    @Inject
    lateinit var localToFirestoreMigration: LocalToFirestoreMigration

    private var biometricAuthenticated = false
    private var householdChecked = false

    private var navDestination: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        navDestination = intent.getStringExtra("nav_destination")

        val currentUser = firebaseAuth.currentUser

        // Skip biometric in sandbox mode
        if (sandboxPreferences.isSandboxCached) {
            biometricAuthenticated = true
            setupContent()
            return
        }

        if (currentUser != null && !biometricAuthenticated) {
            // Check if biometric is enabled for this user
            lifecycleScope.launch {
                val biometricEnabled = biometricPreferences.isBiometricEnabled(currentUser.uid).firstOrNull() ?: false
                if (biometricEnabled && BiometricHelper.canAuthenticate(this@MainActivity)) {
                    BiometricHelper.authenticate(
                        activity = this@MainActivity,
                        onSuccess = {
                            biometricAuthenticated = true
                            setupContent()
                        },
                        onError = {
                            // User cancelled or failed - still show app but could restrict
                            biometricAuthenticated = true
                            setupContent()
                        }
                    )
                } else {
                    biometricAuthenticated = true
                    setupContent()
                }
            }
        } else {
            biometricAuthenticated = true
            setupContent()
        }
    }

    private fun setupContent() {
        setContent {
            ExpenseTrackerTheme(themePreferences = themePreferences) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isSandbox = sandboxPreferences.isSandboxCached
                val startDestination: Any = remember {
                    if (firebaseAuth.currentUser != null || isSandbox) DashboardRoute else LoginRoute
                }

                // Check if user has a household — redirect to setup if not (skip in sandbox).
                //
                // The `householdChecked` guard must NOT be part of this condition: it is a
                // plain var, but `navBackStackEntry` above is observable state, so the first
                // navigation recomposes, the condition flips, the LaunchedEffect leaves the
                // composition and its in-flight Firestore read is cancelled. That surfaced as
                // "The coroutine scope left the composition" and was then misread as "no
                // household", bouncing signed-in users to setup. Keep the once-only guard
                // inside the effect so the effect itself is never conditionally disposed.
                if (firebaseAuth.currentUser != null && !isSandbox) {
                    LaunchedEffect(Unit) {
                        if (!householdChecked) {
                            val uid = firebaseAuth.currentUser?.uid
                            if (uid != null) {
                                val hId = try {
                                    householdRepository.getUserHouseholdId(uid)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // A failed lookup is not the same as "has no household" —
                                    // don't redirect on it.
                                    Log.w("MainActivity", "Household check failed; staying put", e)
                                    householdChecked = true
                                    return@LaunchedEffect
                                }
                                householdChecked = true
                                if (hId != null) {
                                    // Budgets/goals/recurring moved from Room to Firestore;
                                    // lift any pre-existing local rows up once.
                                    localToFirestoreMigration.runIfNeeded(hId)
                                }
                                if (hId == null) {
                                    navController.navigate(HouseholdSetupRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                }

                val pendingDestination = remember { navDestination }
                if (pendingDestination == "sms_report" && startDestination == DashboardRoute) {
                    LaunchedEffect(Unit) {
                        navController.navigate(NotificationsRoute)
                    }
                }

                // Allowlist, not a denylist: the bar belongs to the four tabs that can be
                // reached from it, so anything pushed on top of them hides it by default.
                // This was previously a list of routes to exclude, which meant every new
                // screen showed the bar until someone remembered to add it — Settings,
                // Budget, Savings, Reminder and eight others never were.
                //
                // `hasRoute` rather than a substring match on the route string: routes with
                // arguments serialise as ".../ExpenseListRoute/{personFilter}", and
                // "SmartInsightsRoute" contains "InsightsRoute", so matching on text is a
                // false-positive waiting to happen.
                val showBottomBar = navBackStackEntry?.destination?.let { destination ->
                    bottomNavItems.any { destination.hasRoute(it.route::class) }
                } ?: false

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onItemClick = { destination ->
                                    navController.navigate(destination) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onFabClick = {
                                    navController.navigate(AddEditExpenseRoute())
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    ExpenseTrackerNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
