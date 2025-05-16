package com.petar.smrdici.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Calendar : Screen("calendar")
    object Lists : Screen("lists")
    object Finance : Screen("finance")
    object ListDetails : Screen("list_details/{listId}") {
        fun createRoute(listId: String) = "list_details/$listId"
    }
    object BudgetSettings : Screen("budget_settings")
    
    object EditAccount : Screen("edit_account/{accountId}")
    
    object AddAccount : Screen("add_account")
    
    object Profile : Screen("profile")
    object AddEvent : Screen("add_event")
    object AddExpense : Screen("add_expense")
    object AddIncome : Screen("add_income")
    
    object Transfer : Screen("transfer")
    
    object ExpenseCategories : Screen("expense_categories")
    object IncomeCategories : Screen("income_categories")
    
    object EditExpense : Screen("edit_expense/{expenseId}")
    object EditIncome : Screen("edit_income/{incomeId}")
} 