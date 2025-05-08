package com.petar.smrdici.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.ui.components.MainLayout
import com.petar.smrdici.ui.screens.addAccount.AddAccountScreen
import com.petar.smrdici.ui.screens.auth.LoginScreen
import com.petar.smrdici.ui.screens.budget.AddExpenseScreen
import com.petar.smrdici.ui.screens.budget.AddIncomeScreen
import com.petar.smrdici.ui.screens.budget.BudgetListScreen
import com.petar.smrdici.ui.screens.budget.BudgetScreen
import com.petar.smrdici.ui.screens.budget.EditExpenseScreen
import com.petar.smrdici.ui.screens.budget.EditIncomeScreen
import com.petar.smrdici.ui.screens.budget.BudgetViewModel
import com.petar.smrdici.ui.screens.calendar.AddEventScreen
import com.petar.smrdici.ui.screens.calendar.CalendarScreen
import com.petar.smrdici.ui.screens.editAccount.EditAccountScreen
import com.petar.smrdici.ui.screens.home.HomeScreen
import com.petar.smrdici.ui.screens.lists.ListDetailsScreen
import com.petar.smrdici.ui.screens.lists.ListsScreen
import com.petar.smrdici.ui.screens.profile.ProfileScreen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsScreen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import com.petar.smrdici.ui.screens.settings.ExpenseCategoriesScreen
import com.petar.smrdici.ui.screens.settings.IncomeCategoriesScreen
import com.petar.smrdici.ui.screens.transfer.TransferScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    val context = LocalContext.current
    
    // Create repositories and settings view model
    val expenseRepository = remember { ExpenseRepository.getInstance() }
    val incomeRepository = remember { IncomeRepository.getInstance() }
    val settingsViewModel = remember { 
        BudgetSettingsViewModel.Factory(context).create(BudgetSettingsViewModel::class.java)
    }
    
    // Create BudgetViewModel factory
    val budgetViewModelFactory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return BudgetViewModel(
                    expenseRepository = expenseRepository,
                    incomeRepository = incomeRepository,
                    settingsViewModel = settingsViewModel,
                    applicationContext = context
                ) as T
            }
        }
    }
    // Create BudgetViewModel ONCE here and share it
    val budgetViewModel: BudgetViewModel = viewModel(factory = budgetViewModelFactory)

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
        
        composable(route = Screen.Budget.route) {
            MainLayout {
                BudgetScreen(navController = navController, budgetViewModel = budgetViewModel)
            }
        }
        
        // Nova ruta za listu budžeta
        composable(route = Screen.BudgetList.route) {
            MainLayout {
                BudgetListScreen(navController = navController, budgetViewModel = budgetViewModel)
            }
        }
        
        // Ruta za detalje budžeta sa parametrom ID
        composable(
            route = "${Screen.BudgetDetail.route}/{budgetId}",
            arguments = listOf(
                navArgument("budgetId") { type = NavType.StringType }
            )
        ) { 
            // Privremena implementacija - ne koristimo budgetId dok ne implementiramo detaljni ekran
            MainLayout {
                BudgetListScreen(navController = navController)
            }
        }
        
        // Ruta za dodavanje budžeta
        composable(
            route = "${Screen.AddBudget.route}?type={type}",
            arguments = listOf(
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = "expense"
                    nullable = true
                }
            )
        ) {
            // Privremeni kod za navigaciju nazad - ne koristimo type parametar
            LaunchedEffect(key1 = true) {
                navController.navigateUp()
            }
            
            // Prazan ekran dok se ne izvrši navigacija
            Box(modifier = Modifier.fillMaxSize())
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
        
        composable(route = Screen.Profile.route) {
            MainLayout {
                ProfileScreen(navController = navController)
            }
        }
        
        composable(route = Screen.AddEvent.route) {
            AddEventScreen(navController = navController)
        }
        
        composable(route = Screen.AddExpense.route) {
            AddExpenseScreen(navController = navController)
        }
        
        composable(route = Screen.AddIncome.route) {
            AddIncomeScreen(navController = navController)
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
                listId = listId
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
            val expenses = budgetViewModel.expenses.value
            Log.d("NavGraph", "Current expenses list: ${expenses.map { it.id }}")
            val expense = expenses.find { it.id == expenseId }
            if (expense != null) {
                Log.d("NavGraph", "Expense found: $expense")
                EditExpenseScreen(
                    expense = expense,
                    onNavigateBack = { navController.popBackStack() },
                    budgetViewModel = budgetViewModel
                )
            } else {
                Log.e("NavGraph", "Expense with id $expenseId not found! Current expense IDs: ${expenses.map { it.id }}")
            }
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
            val incomes = budgetViewModel.incomes.value
            Log.d("NavGraph", "Current incomes list: ${incomes.map { it.id }}")
            val income = incomes.find { it.id == incomeId }
            if (income != null) {
                Log.d("NavGraph", "Income found: $income")
                EditIncomeScreen(
                    income = income,
                    onNavigateBack = { navController.popBackStack() },
                    budgetViewModel = budgetViewModel
                )
            } else {
                Log.e("NavGraph", "Income with id $incomeId not found! Current income IDs: ${incomes.map { it.id }}")
            }
        }
    }
} 