package com.petar.smrdici.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExpenseViewModel : ViewModel() {
    private val expenseRepository = ExpenseRepository.getInstance()
    
    private val _currentExpense = MutableStateFlow<Expense?>(null)
    val currentExpense: StateFlow<Expense?> = _currentExpense
    
    fun getExpenseById(expenseId: String) {
        viewModelScope.launch {
            try {
                val expense = expenseRepository.getExpenseById(expenseId)
                _currentExpense.value = expense
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepository.updateExpense(expense)
                _currentExpense.value = expense
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
} 