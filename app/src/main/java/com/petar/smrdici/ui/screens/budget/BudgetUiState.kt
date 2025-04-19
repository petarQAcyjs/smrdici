package com.petar.smrdici.ui.screens.budget

import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income

/**
 * Stanje korisničkog interfejsa za BudgetViewModel
 */
data class BudgetUiState(
    val expenses: List<Expense> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) 