package com.petar.smrdici.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Budget : Screen("budget")
    object Calendar : Screen("calendar")
    object Lists : Screen("lists")
    object Profile : Screen("profile")
    object AddEvent : Screen("add_event")
} 