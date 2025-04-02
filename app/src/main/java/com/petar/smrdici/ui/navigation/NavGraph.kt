package com.petar.smrdici.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.petar.smrdici.ui.components.MainLayout
import com.petar.smrdici.ui.screens.auth.LoginScreen
import com.petar.smrdici.ui.screens.budget.BudgetScreen
import com.petar.smrdici.ui.screens.calendar.CalendarScreen
import com.petar.smrdici.ui.screens.home.HomeScreen
import com.petar.smrdici.ui.screens.lists.ListDetailsScreen
import com.petar.smrdici.ui.screens.lists.ListsScreen
import com.petar.smrdici.ui.screens.profile.ProfileScreen
import com.petar.smrdici.ui.screens.calendar.AddEventScreen
import com.petar.smrdici.ui.screens.settings.BudgetSettingsScreen

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
    }
} 