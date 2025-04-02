package com.petar.smrdici.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Budget : Screen("budget")
    data object Calendar : Screen("calendar")
    data object Lists : Screen("lists")
    data object ListDetails : Screen("list/{listId}") {
        fun createRoute(listId: String) = "list/$listId"
    }
    data object Profile : Screen("profile")
    data object AddEvent : Screen("add_event")
    data object BudgetSettings : Screen("budget_settings")
} 