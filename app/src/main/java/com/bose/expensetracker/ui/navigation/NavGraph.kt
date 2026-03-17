package com.bose.expensetracker.ui.navigation

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bose.expensetracker.R
import com.bose.expensetracker.ui.screen.auth.AuthViewModel
import com.bose.expensetracker.ui.screen.auth.HouseholdSetupScreen
import com.bose.expensetracker.ui.screen.auth.LoginScreen
import com.bose.expensetracker.ui.screen.auth.PhoneAuthScreen
import com.bose.expensetracker.ui.screen.auth.SignUpScreen
import com.bose.expensetracker.ui.screen.category.CategoryScreen
import com.bose.expensetracker.ui.screen.category.CategoryViewModel
import com.bose.expensetracker.ui.screen.dashboard.DashboardScreen
import com.bose.expensetracker.ui.screen.dashboard.DashboardViewModel
import com.bose.expensetracker.ui.screen.expense.AddEditExpenseScreen
import com.bose.expensetracker.ui.screen.expense.AddEditExpenseViewModel
import com.bose.expensetracker.ui.screen.expense.ExpenseListScreen
import com.bose.expensetracker.ui.screen.expense.ExpenseListViewModel
import com.bose.expensetracker.ui.screen.household.HouseholdScreen
import com.bose.expensetracker.ui.screen.household.HouseholdViewModel
import com.bose.expensetracker.ui.screen.insights.InsightsScreen
import com.bose.expensetracker.ui.screen.insights.InsightsViewModel
import com.bose.expensetracker.ui.screen.networth.NetWorthScreen
import com.bose.expensetracker.ui.screen.networth.NetWorthViewModel
import com.bose.expensetracker.ui.screen.receipt.ReceiptScannerScreen
import com.bose.expensetracker.ui.screen.receipt.ReceiptScannerViewModel
import com.bose.expensetracker.ui.screen.settings.SettingsScreen
import com.bose.expensetracker.ui.screen.settings.SettingsViewModel
import com.bose.expensetracker.ui.screen.voice.VoiceExpenseParser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ExpenseTrackerNavGraph(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier
) {
    // Scope authViewModel to the activity so it's shared across all auth screens
    val activity = LocalContext.current as Activity
    val authViewModel: AuthViewModel = hiltViewModel(viewModelStoreOwner = activity as androidx.lifecycle.ViewModelStoreOwner)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<LoginRoute> {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                },
                onNavigateToHouseholdSetup = {
                    navController.navigate(HouseholdSetupRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                },
                onGoogleSignInClick = {
                    coroutineScope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(context.getString(R.string.default_web_client_id))
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val result = credentialManager.getCredential(context as Activity, request)
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                            val idToken = googleIdTokenCredential.idToken
                            authViewModel.signInWithGoogle(idToken)
                        } catch (e: GetCredentialCancellationException) {
                            Log.d("GoogleSignIn", "User cancelled Google Sign-In")
                        } catch (e: Exception) {
                            Log.e("GoogleSignIn", "Google Sign-In failed", e)
                            authViewModel.handleGoogleSignInError(e.message ?: "Google Sign-In failed")
                        }
                    }
                },
                onPhoneSignInClick = { navController.navigate(PhoneAuthRoute) }
            )
        }

        composable<PhoneAuthRoute> {
            PhoneAuthScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                },
                onNavigateToHouseholdSetup = {
                    navController.navigate(HouseholdSetupRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<SignUpRoute> {
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHouseholdSetup = {
                    navController.navigate(HouseholdSetupRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<HouseholdSetupRoute> {
            HouseholdSetupScreen(
                viewModel = authViewModel,
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(HouseholdSetupRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<DashboardRoute> {
            val viewModel: DashboardViewModel = hiltViewModel()
            DashboardScreen(
                viewModel = viewModel,
                onAddExpense = { navController.navigate(AddEditExpenseRoute()) },
                onEditExpense = { id -> navController.navigate(AddEditExpenseRoute(expenseId = id)) },
                onViewAllExpenses = { navController.navigate(ExpenseListRoute) }
            )
        }

        composable<ExpenseListRoute> {
            val viewModel: ExpenseListViewModel = hiltViewModel()
            ExpenseListScreen(
                viewModel = viewModel,
                onAddExpense = { navController.navigate(AddEditExpenseRoute()) },
                onEditExpense = { id -> navController.navigate(AddEditExpenseRoute(expenseId = id)) }
            )
        }

        composable<AddEditExpenseRoute> {
            val viewModel: AddEditExpenseViewModel = hiltViewModel()

            val voiceLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spokenText = result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull() ?: return@rememberLauncherForActivityResult

                    val parsed = VoiceExpenseParser.parse(spokenText)
                    viewModel.populateFromVoice(parsed.amount, parsed.categoryHint, parsed.rawText)
                }
            }

            // Observe receipt scanner results from savedStateHandle
            val receiptAmount = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Double>("receipt_amount")
            val receiptDate = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Long>("receipt_date")
            if (receiptAmount != null || receiptDate != null) {
                viewModel.populateFromReceipt(receiptAmount, receiptDate)
                navController.currentBackStackEntry?.savedStateHandle?.remove<Double>("receipt_amount")
                navController.currentBackStackEntry?.savedStateHandle?.remove<Long>("receipt_date")
            }

            AddEditExpenseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenReceiptScanner = { navController.navigate(ReceiptScannerRoute) },
                onOpenVoiceInput = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the expense, e.g., 'Spent 50 dollars on groceries'")
                    }
                    voiceLauncher.launch(intent)
                }
            )
        }

        composable<CategoryRoute> {
            val viewModel: CategoryViewModel = hiltViewModel()
            CategoryScreen(viewModel = viewModel)
        }

        composable<InsightsRoute> {
            val viewModel: InsightsViewModel = hiltViewModel()
            InsightsScreen(viewModel = viewModel)
        }

        composable<ReceiptScannerRoute> {
            val viewModel: ReceiptScannerViewModel = hiltViewModel()
            ReceiptScannerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onUseResult = { amount, date ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set("receipt_amount", amount)
                        set("receipt_date", date)
                    }
                }
            )
        }

        composable<NetWorthRoute> {
            val viewModel: NetWorthViewModel = hiltViewModel()
            NetWorthScreen(viewModel = viewModel)
        }

        composable<AddEditAssetRoute> {
            val viewModel: NetWorthViewModel = hiltViewModel()
            NetWorthScreen(viewModel = viewModel)
        }

        composable<HouseholdManageRoute> {
            val viewModel: HouseholdViewModel = hiltViewModel()
            HouseholdScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onHouseholdSwitched = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(DashboardRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<SettingsRoute> {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onSignOut = {
                    navController.navigate(LoginRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onHouseholdSwitched = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(DashboardRoute) { inclusive = true }
                    }
                },
                onNavigateToHousehold = {
                    navController.navigate(HouseholdManageRoute)
                }
            )
        }
    }
}
