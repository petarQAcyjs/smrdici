package com.petar.smrdici.ui.screens.budget

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.ui.screens.settings.BudgetSettingsViewModel
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class BudgetViewModel(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsViewModel: BudgetSettingsViewModel
) : ViewModel() {
    
    // UI стање
    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()
    
    // Стање за период
    private val _selectedPeriodIndex = MutableStateFlow(2) // Подразумевано месечно
    val selectedPeriodIndex: StateFlow<Int> = _selectedPeriodIndex.asStateFlow()
    
    // Стање за период као Period објекат
    private val _selectedPeriod = MutableStateFlow(Period.MONTHLY)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod.asStateFlow()
    
    // Остала стања из оригиналаног ViewModel-а
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    val incomes: StateFlow<List<Income>> = _incomes.asStateFlow()
    
    // Додајемо ове променљиве у BudgetViewModel
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    init {
        // Учитавамо трансакције
        loadExpenses()
        loadIncomes()
        
        // Пратимо промене периода из подешавања
        viewModelScope.launch {
            settingsViewModel.period.collectLatest { period ->
                val index = when (period) {
                    Period.DAILY -> 0
                    Period.WEEKLY -> 1
                    Period.MONTHLY -> 2
                    Period.YEARLY -> 3
                    Period.CUSTOM -> 4
                    Period.ALL -> 5
                }
                Log.d("BudgetViewModel", "Ажурирам период из подешавања: $period, индекс: $index")
                if (_selectedPeriodIndex.value != index) {
                    _selectedPeriodIndex.value = index
                    _selectedPeriod.value = period
                    Log.d("BudgetViewModel", "Учитавам трансакције за период: $index")
                    loadTransactions()
                }
            }
        }
    }
    
    fun updatePeriodIndex(index: Int) {
        if (_selectedPeriodIndex.value != index) {
            _selectedPeriodIndex.value = index
            
            // Ажурирамо и подешавања
            val period = when (index) {
                0 -> Period.DAILY
                1 -> Period.WEEKLY
                2 -> Period.MONTHLY
                3 -> Period.YEARLY
                4 -> Period.CUSTOM
                5 -> Period.ALL
                else -> Period.MONTHLY
            }
            
            _selectedPeriod.value = period
            
            // Ажурирамо подешавања
            viewModelScope.launch {
                settingsViewModel.setPeriod(period)
                // Поново учитај податке са новим периодом
                loadTransactions()
            }
        }
    }
    
    private fun loadTransactions() {
        loadExpenses()
        loadIncomes()
    }
    
    private fun loadExpenses() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Користимо постојећу методу getAllExpenses и филтрирамо резултате
            expenseRepository.getAllExpenses().collect { allExpenses ->
                val filteredExpenses = when (_selectedPeriod.value) {
                    Period.ALL -> allExpenses
                    else -> {
                        val (startDate, endDate) = calculatePeriodDates(_selectedPeriod.value)
                        allExpenses.filter { expense ->
                            val expenseDate = expense.date?.toDate()
                            expenseDate != null && expenseDate >= startDate && expenseDate <= endDate
                        }
                    }
                }
                
                _expenses.value = filteredExpenses
                _isLoading.value = false
            }
        }
    }
    
    private fun calculatePeriodDates(period: Period): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        calendar.apply {
            when (period) {
                Period.DAILY -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.WEEKLY -> {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.MONTHLY -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.YEARLY -> {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.CUSTOM -> {
                    // Користимо прилагођени датум почетка периода
                    val customDay = settingsViewModel.customPeriodStartDay.value
                    
                    // Ако је данашњи дан пре прилагођеног дана, идемо на прошли месец
                    if (get(Calendar.DAY_OF_MONTH) < customDay) {
                        add(Calendar.MONTH, -1)
                    }
                    
                    // Постављамо дан на прилагођени дан
                    set(Calendar.DAY_OF_MONTH, customDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                Period.ALL -> {
                    // За ALL враћамо веома стари датум као почетак
                    set(Calendar.YEAR, 2000)
                    set(Calendar.MONTH, 0)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            }
        }
        
        val startDate = calendar.time
        return Pair(startDate, endDate)
    }
    
    private fun loadIncomes() {
        viewModelScope.launch {
            _isLoading.value = true
            incomeRepository.getAllIncomes().collect { incomeList ->
                _incomes.value = incomeList
                _isLoading.value = false
            }
        }
    }
    
    // Фабрика за креирање ViewModel-а
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
                val expenseRepository = ExpenseRepository.getInstance()
                val incomeRepository = IncomeRepository.getInstance()
                val settingsViewModel = BudgetSettingsViewModel.Factory(context)
                    .create(BudgetSettingsViewModel::class.java)
                return BudgetViewModel(expenseRepository, incomeRepository, settingsViewModel) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// UI стање за буџет
data class BudgetUiState(
    val isLoading: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val error: String? = null
) 