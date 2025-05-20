package com.petar.smrdici.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.IncomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IncomeViewModel : ViewModel() {
    private val incomeRepository = IncomeRepository.getInstance()
    
    private val _currentIncome = MutableStateFlow<Income?>(null)
    val currentIncome: StateFlow<Income?> = _currentIncome
    
    fun getIncomeById(incomeId: String) {
        viewModelScope.launch {
            try {
                val income = incomeRepository.getIncomeById(incomeId)
                _currentIncome.value = income
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updateIncome(income: Income) {
        viewModelScope.launch {
            try {
                incomeRepository.updateIncome(income)
                _currentIncome.value = income
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
} 