package com.petar.smrdici.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.petar.smrdici.ui.components.MainLayout
import com.petar.smrdici.ui.screens.auth.LoginScreen
import com.petar.smrdici.ui.screens.budget.BudgetScreen
import com.petar.smrdici.ui.screens.calendar.CalendarScreen
import com.petar.smrdici.ui.screens.home.HomeScreen
import com.petar.smrdici.ui.screens.lists.ListsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        
        composable(route = Screen.Home.route) {
            MainLayout(navController = navController) {
                HomeScreen(navController = navController)
            }
        }
        
        composable(route = Screen.Budget.route) {
            MainLayout(navController = navController) {
                BudgetScreen(navController = navController)
            }
        }
        
        composable(route = Screen.Calendar.route) {
            MainLayout(navController = navController) {
                CalendarScreen(navController = navController)
            }
        }
        
        composable(route = Screen.Lists.route) {
            MainLayout(navController = navController) {
                ListsScreen(navController = navController)
            }
        }
    }
} 