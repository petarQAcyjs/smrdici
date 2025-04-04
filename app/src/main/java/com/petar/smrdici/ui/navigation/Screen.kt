package com.petar.smrdici.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Calendar : Screen("calendar")
    object Lists : Screen("lists")
    object ListDetails : Screen("list_details/{listId}") {
        fun createRoute(listId: String) = "list_details/$listId"
    }
    object Settings : Screen("settings")
    object BudgetSettings : Screen("budget_settings")
    object ThemeSettings : Screen("theme_settings")
    
    object EditAccount : Screen("edit_account/{accountId}") {
        fun createRoute(accountId: String) = "edit_account/$accountId"
    }
    
    object Budget : Screen("budget")
    object Profile : Screen("profile")
    object AddEvent : Screen("add_event")
    object AddExpense : Screen("add_expense")
    object AddIncome : Screen("add_income")
} 