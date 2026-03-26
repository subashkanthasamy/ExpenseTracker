package com.bose.expensetracker

import android.os.Bundle
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bose.expensetracker.data.preferences.BiometricPreferences
import com.bose.expensetracker.data.preferences.ThemePreferences
import com.bose.expensetracker.ui.navigation.BottomNavBar
import com.bose.expensetracker.ui.navigation.DashboardRoute
import com.bose.expensetracker.ui.navigation.ExpenseTrackerNavGraph
import com.bose.expensetracker.ui.navigation.LoginRoute
import com.bose.expensetracker.ui.navigation.NotificationsRoute
import com.bose.expensetracker.ui.screen.auth.BiometricHelper
import com.bose.expensetracker.ui.theme.ExpenseTrackerTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
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

    private var biometricAuthenticated = false

    private var navDestination: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        navDestination = intent.getStringExtra("nav_destination")

        val currentUser = firebaseAuth.currentUser

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

                val startDestination: Any = remember {
                    if (firebaseAuth.currentUser != null) DashboardRoute else LoginRoute
                }

                val pendingDestination = remember { navDestination }
                if (pendingDestination == "sms_report" && startDestination == DashboardRoute) {
                    LaunchedEffect(Unit) {
                        navController.navigate(NotificationsRoute)
                    }
                }

                val showBottomBar = currentRoute?.let { route ->
                    !route.contains("Login") &&
                    !route.contains("SignUp") &&
                    !route.contains("PhoneAuth") &&
                    !route.contains("HouseholdSetup") &&
                    !route.contains("AddEditExpense") &&
                    !route.contains("ReceiptScanner")
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
