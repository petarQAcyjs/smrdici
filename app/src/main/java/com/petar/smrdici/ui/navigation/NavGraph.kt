package com.petar.smrdici.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.petar.smrdici.ui.components.MainLayout
import com.petar.smrdici.ui.screens.auth.LoginScreen
import com.petar.smrdici.ui.screens.budget.AddExpenseScreen
import com.petar.smrdici.ui.screens.budget.AddIncomeScreen
import com.petar.smrdici.ui.screens.budget.BudgetScreen
import com.petar.smrdici.ui.screens.calendar.CalendarScreen
import com.petar.smrdici.ui.screens.home.HomeScreen
import com.petar.smrdici.ui.screens.lists.ListDetailsScreen
import com.petar.smrdici.ui.screens.lists.ListsScreen
import com.petar.smrdici.ui.screens.profile.ProfileScreen
import com.petar.smrdici.ui.screens.calendar.AddEventScreen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsScreen
import com.petar.smrdici.ui.screens.editAccount.EditAccountScreen
import com.petar.smrdici.ui.screens.addAccount.AddAccountScreen
import com.petar.smrdici.ui.screens.settings.ExpenseCategoriesScreen
import com.petar.smrdici.ui.screens.settings.IncomeCategoriesScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
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
                BudgetScreen(navController = navController)
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
    }
} 