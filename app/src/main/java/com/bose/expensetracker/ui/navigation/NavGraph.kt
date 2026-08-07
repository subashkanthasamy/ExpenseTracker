package com.bose.expensetracker.ui.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.bose.expensetracker.R
import com.bose.expensetracker.ui.screen.auth.AuthViewModel
import com.bose.expensetracker.ui.screen.auth.HouseholdSetupScreen
import com.bose.expensetracker.ui.screen.auth.LoginScreen
import com.bose.expensetracker.ui.screen.auth.PhoneAuthScreen
import com.bose.expensetracker.ui.screen.auth.SignUpScreen
import com.bose.expensetracker.ui.screen.budget.BudgetScreen
import com.bose.expensetracker.ui.screen.budget.BudgetViewModel
import com.bose.expensetracker.ui.screen.category.CategoryScreen
import com.bose.expensetracker.ui.screen.category.CategoryViewModel
import com.bose.expensetracker.ui.screen.coach.FinancialCoachScreen
import com.bose.expensetracker.ui.screen.coach.FinancialCoachViewModel
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
import com.bose.expensetracker.ui.screen.insights.SmartInsightsScreen
import com.bose.expensetracker.ui.screen.networth.NetWorthScreen
import com.bose.expensetracker.ui.screen.networth.NetWorthViewModel
import com.bose.expensetracker.ui.screen.notification.NotificationsScreen
import com.bose.expensetracker.ui.screen.notification.NotificationsViewModel
import com.bose.expensetracker.ui.screen.receipt.ReceiptScannerScreen
import com.bose.expensetracker.ui.screen.receipt.ReceiptScannerViewModel
import com.bose.expensetracker.ui.screen.recurring.RecurringScreen
import com.bose.expensetracker.ui.screen.recurring.RecurringViewModel
import com.bose.expensetracker.ui.screen.reminder.ReminderScreen
import com.bose.expensetracker.ui.screen.reminder.ReminderViewModel
import com.bose.expensetracker.ui.screen.savings.SavingsScreen
import com.bose.expensetracker.ui.screen.savings.SavingsViewModel
import com.bose.expensetracker.ui.screen.settings.SettingsScreen
import com.bose.expensetracker.ui.screen.settings.SettingsViewModel
import com.bose.expensetracker.ui.screen.voice.VoiceExpenseParser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

                            // Try One Tap first, fallback to Sign In With Google button
                            val result = try {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(context.getString(R.string.default_web_client_id))
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()
                                credentialManager.getCredential(context as Activity, request)
                            } catch (e: NoCredentialException) {
                                // No account matched One Tap — fall back to the explicit
                                // "Sign in with Google" flow. Keep the original exception:
                                // if the fallback also fails, One Tap's reason is the useful one.
                                Log.d("GoogleSignIn", "One Tap found no credential, falling back", e)
                                val signInOption = GetSignInWithGoogleOption.Builder(
                                    context.getString(R.string.default_web_client_id)
                                ).build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(signInOption)
                                    .build()
                                credentialManager.getCredential(context as Activity, request)
                            }

                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                            authViewModel.signInWithGoogle(googleIdTokenCredential.idToken)
                        } catch (e: GetCredentialCancellationException) {
                            // Credential Manager also reports a provider-side refusal as a
                            // cancellation, so log the detail rather than assuming the user
                            // dismissed the sheet — a signing cert missing from the Firebase
                            // project shows up here and is otherwise invisible.
                            Log.i("GoogleSignIn", "Google Sign-In cancelled or refused by provider: ${e.type} ${e.errorMessage}", e)
                        } catch (e: NoCredentialException) {
                            // Fallback path failed too — usually no Google account on the
                            // device, or this build's signing certificate is not registered
                            // for the Firebase project's Android OAuth client.
                            Log.e("GoogleSignIn", "No Google credential available: ${e.type} ${e.errorMessage}", e)
                            authViewModel.handleGoogleSignInError(
                                "No Google account available for this app. Check that a Google account is added to the device and that this build's signing certificate is registered in Firebase."
                            )
                        } catch (e: Exception) {
                            Log.e("GoogleSignIn", "Google Sign-In failed (${e.javaClass.simpleName})", e)
                            authViewModel.handleGoogleSignInError(e.message ?: "Google Sign-In failed")
                        }
                    }
                },
                onPhoneSignInClick = { navController.navigate(PhoneAuthRoute) },
                onSandboxClick = { authViewModel.enterSandbox() }
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
                onEditExpense = { id -> navController.navigate(AddEditExpenseRoute(expenseId = id)) },
                onViewAllExpenses = { navController.navigate(ExpenseListRoute()) },
                onNavigateToNotifications = { navController.navigate(NotificationsRoute) },
                onNavigateToReminders = { navController.navigate(ReminderRoute) },
                onNavigateToHouseholdSetup = {
                    navController.navigate(HouseholdSetupRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToInsights = { navController.navigate(SmartInsightsRoute) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToCoach = { navController.navigate(FinancialCoachRoute) }
            )
        }

        composable<ExpenseListRoute> { backStackEntry ->
            val viewModel: ExpenseListViewModel = hiltViewModel()
            val personFilter = backStackEntry.toRoute<ExpenseListRoute>().personFilter
            // Keyed on the argument so arriving from a different member re-applies; the guard
            // stays inside the effect so a recomposition can't cancel it mid-flight.
            LaunchedEffect(personFilter) {
                if (personFilter != null) viewModel.setPersonFilter(personFilter)
            }
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
            InsightsScreen(
                viewModel = viewModel,
                onPersonSelected = { uid ->
                    navController.navigate(ExpenseListRoute(personFilter = uid))
                }
            )
        }

        composable<SmartInsightsRoute> {
            val viewModel: InsightsViewModel = hiltViewModel()
            SmartInsightsScreen(
                viewModel = viewModel,
                onNavigateToAnalytics = { navController.navigate(AnalyticsRoute) },
                onNavigateToBudget = { navController.navigate(BudgetRoute) }
            )
        }

        composable<AnalyticsRoute> {
            val viewModel: InsightsViewModel = hiltViewModel()
            InsightsScreen(
                viewModel = viewModel,
                onPersonSelected = { uid ->
                    navController.navigate(ExpenseListRoute(personFilter = uid))
                }
            )
        }

        composable<FinancialCoachRoute> {
            val viewModel: FinancialCoachViewModel = hiltViewModel()
            FinancialCoachScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
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

        composable<NotificationsRoute> {
            val viewModel: NotificationsViewModel = hiltViewModel()
            NotificationsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onEditExpense = { id -> navController.navigate(AddEditExpenseRoute(expenseId = id)) }
            )
        }

        composable<ReminderRoute> {
            val viewModel: ReminderViewModel = hiltViewModel()
            ReminderScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<BudgetRoute> {
            val viewModel: BudgetViewModel = hiltViewModel()
            BudgetScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<RecurringRoute> {
            val viewModel: RecurringViewModel = hiltViewModel()
            RecurringScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<SavingsRoute> {
            val viewModel: SavingsViewModel = hiltViewModel()
            SavingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
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
                },
                onNavigateToSetup = {
                    navController.navigate(HouseholdSetupRoute) {
                        popUpTo(0) { inclusive = true }
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
                },
                onNavigateToReminders = {
                    navController.navigate(ReminderRoute)
                },
                onNavigateToBudgets = {
                    navController.navigate(BudgetRoute)
                },
                onNavigateToRecurring = {
                    navController.navigate(RecurringRoute)
                },
                onNavigateToSavings = {
                    navController.navigate(SavingsRoute)
                }
            )
        }
    }
}
