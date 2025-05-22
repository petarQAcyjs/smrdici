package com.petar.smrdici.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.SettingsRepository
import com.petar.smrdici.ui.components.MainLayout
import com.petar.smrdici.ui.screens.addAccount.AddAccountScreen
import com.petar.smrdici.ui.screens.auth.LoginScreen
import com.petar.smrdici.ui.screens.budget.AddExpenseScreen
import com.petar.smrdici.ui.screens.budget.AddIncomeScreen
import com.petar.smrdici.ui.screens.budget.EditExpenseScreen
import com.petar.smrdici.ui.screens.budget.EditIncomeScreen
import com.petar.smrdici.ui.screens.calendar.AddEventScreen
import com.petar.smrdici.ui.screens.calendar.CalendarScreen
import com.petar.smrdici.ui.screens.editAccount.EditAccountScreen
import com.petar.smrdici.ui.screens.home.HomeScreen
import com.petar.smrdici.ui.screens.lists.ListDetailsScreen
import com.petar.smrdici.ui.screens.lists.ListsScreen
import com.petar.smrdici.ui.screens.lists.ListsViewModel
import com.petar.smrdici.ui.screens.profile.ProfileScreen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsScreen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import com.petar.smrdici.ui.screens.settings.ExpenseCategoriesScreen
import com.petar.smrdici.ui.screens.settings.IncomeCategoriesScreen
import com.petar.smrdici.ui.screens.transfer.TransferScreen
import com.petar.smrdici.ui.screens.finance.FinanceScreen
import com.petar.smrdici.ui.screens.finance.FinanceViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    val context = LocalContext.current
    
    // Create repositories and settings view model
    val expenseRepository = remember { ExpenseRepository.getInstance() }
    val incomeRepository = remember { IncomeRepository.getInstance() }
    val settingsRepository = remember { SettingsRepository.getInstance(context) }
    val settingsViewModel = remember { 
        BudgetSettingsViewModel.Factory(context).create(BudgetSettingsViewModel::class.java)
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        
        composable(route = Screen.Home.route) {
            MainLayout {
                HomeScreen(navController = navController)
            }
        }
        
        composable(route = Screen.Calendar.route) {
            MainLayout {
                CalendarScreen(navController = navController)
            }
        }
        
        composable(route = Screen.Lists.route) {
            MainLayout {
                ListsScreen(navController = navController)
            }
        }
        
        composable(route = Screen.Finance.route) {
            MainLayout {
                FinanceScreen(
                    navController = navController,
                    viewModel = viewModel(
                        factory = FinanceViewModel.Factory(settingsRepository)
                    )
                )
            }
        }
        
        composable(route = Screen.Profile.route) {
            MainLayout {
                ProfileScreen(navController = navController)
            }
        }
        
        composable(route = Screen.AddEvent.route) {
            AddEventScreen(navController = navController)
        }
        
        composable(route = Screen.AddExpense.route) {
            AddExpenseScreen(
                onNavigateBack = { navController.popBackStack() },
                navController = navController
            )
        }
        
        composable(route = Screen.AddIncome.route) {
            AddIncomeScreen(
                onNavigateBack = { navController.popBackStack() },
                navController = navController
            )
        }
        
        composable(
            route = Screen.ListDetails.route,
            arguments = listOf(
                navArgument("listId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId") ?: ""
            ListDetailsScreen(
                navController = navController,
                listId = listId,
                listsViewModel = viewModel(factory = ListsViewModel.Factory()),
                authViewModel = viewModel()
            )
        }
        
        composable(Screen.BudgetSettings.route) {
            BudgetSettingsScreen(navController = navController)
        }
        
        composable(
            route = Screen.EditAccount.route,
            arguments = listOf(
                navArgument("accountId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
            EditAccountScreen(
                navController = navController,
                accountId = accountId
            )
        }
        
        composable(Screen.AddAccount.route) {
            AddAccountScreen(navController = navController)
        }
        
        // Додајте ову руту за тестирање
        composable("edit_account/{accountId}") { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
            EditAccountScreen(
                navController = navController,
                accountId = accountId
            )
        }
        
        composable(Screen.ExpenseCategories.route) {
            ExpenseCategoriesScreen(navController = navController)
        }
        
        composable(Screen.IncomeCategories.route) {
            IncomeCategoriesScreen(navController = navController)
        }
        
        composable(Screen.Transfer.route) {
            TransferScreen(navController = navController)
        }
        
        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(
                navArgument("expenseId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId") ?: ""
            EditExpenseScreen(
                navController = navController,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.EditIncome.route,
            arguments = listOf(
                navArgument("incomeId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val incomeId = backStackEntry.arguments?.getString("incomeId") ?: ""
            EditIncomeScreen(
                navController = navController,
                incomeId = incomeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
} 