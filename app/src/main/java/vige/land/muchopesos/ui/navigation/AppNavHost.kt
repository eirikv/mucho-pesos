package vige.land.muchopesos.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vige.land.muchopesos.ui.AppContainer
import vige.land.muchopesos.ui.screens.dashboard.DashboardViewModel
import vige.land.muchopesos.ui.screens.expense.ExpenseEditorScreen
import vige.land.muchopesos.ui.screens.expense.ExpenseEditorViewModel
import vige.land.muchopesos.ui.screens.home.HomeShellScreen
import vige.land.muchopesos.ui.screens.onboarding.OnboardingScreen
import vige.land.muchopesos.ui.screens.onboarding.OnboardingViewModel
import vige.land.muchopesos.ui.screens.settings.SettingsScreen
import vige.land.muchopesos.ui.screens.settings.SettingsViewModel
import vige.land.muchopesos.ui.screens.signin.SignInScreen
import vige.land.muchopesos.ui.screens.statistics.StatisticsViewModel

private object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val EXPENSE_EDITOR_PATTERN = "expense_editor?txId={txId}"
    const val SETTINGS = "settings"

    fun expenseEditor(txId: String? = null): String =
        if (txId.isNullOrBlank()) {
            "expense_editor"
        } else {
            "expense_editor?txId=${Uri.encode(txId)}"
        }
}

@Composable
fun AppNavHost(
    container: AppContainer,
) {
    val userSession by container.ledgerRepository.userSession.collectAsStateWithLifecycle()
    val ledgerState by container.ledgerRepository.uiState.collectAsStateWithLifecycle()

    when {
        !ledgerState.isInitialized -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        userSession == null -> {
            SignInScreen(
                googleAuthManager = container.googleAuthManager,
                ledgerRepository = container.ledgerRepository,
            )
        }

        ledgerState.config == null -> {
            val onboardingViewModel: OnboardingViewModel = viewModel(
                key = "onboarding_${userSession?.email.orEmpty()}",
                factory = container.onboardingViewModelFactory(),
            )
            OnboardingScreen(viewModel = onboardingViewModel)
        }

        else -> {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = AppRoutes.DASHBOARD,
            ) {
                composable(route = AppRoutes.DASHBOARD) {
                    val dashboardViewModel: DashboardViewModel = viewModel(
                        factory = container.dashboardViewModelFactory(),
                    )
                    val statisticsViewModel: StatisticsViewModel = viewModel(
                        factory = container.statisticsViewModelFactory(),
                    )
                    HomeShellScreen(
                        dashboardViewModel = dashboardViewModel,
                        statisticsViewModel = statisticsViewModel,
                        onAddExpense = {
                            navController.navigate(AppRoutes.expenseEditor())
                        },
                        onEditExpense = { txId ->
                            navController.navigate(AppRoutes.expenseEditor(txId))
                        },
                        onOpenSettings = {
                            navController.navigate(AppRoutes.SETTINGS)
                        },
                    )
                }

                composable(
                    route = AppRoutes.EXPENSE_EDITOR_PATTERN,
                    arguments = listOf(
                        navArgument("txId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStackEntry ->
                    val editTxId = backStackEntry.arguments?.getString("txId")
                    val editorViewModel: ExpenseEditorViewModel = viewModel(
                        key = "expense_editor_${editTxId ?: "new"}",
                        factory = container.expenseEditorViewModelFactory(editTxId),
                    )
                    ExpenseEditorScreen(
                        viewModel = editorViewModel,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(route = AppRoutes.SETTINGS) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = container.settingsViewModelFactory(),
                    )
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
