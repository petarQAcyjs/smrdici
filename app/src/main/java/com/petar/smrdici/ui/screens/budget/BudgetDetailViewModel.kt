package com.petar.smrdici.ui.screens.budget

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Budget
import com.petar.smrdici.data.model.BudgetType
import com.petar.smrdici.data.model.DisplayBudget
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.data.repository.BudgetRepository
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository
import com.petar.smrdici.data.repository.RepositoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel za upravljanje pojedinačnim budžetom i njegovim detaljima
 */
@Suppress("unused")
class BudgetDetailViewModel(
    private val budgetRepository: BudgetRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository
) : ViewModel() {
    
    private val tag = "BudgetDetailViewModel"
    
    // Trenutni budžet
    private val _currentBudget = MutableStateFlow<Budget?>(null)
    val currentBudget: StateFlow<Budget?> = _currentBudget.asStateFlow()
    
    // DisplayBudget sa detaljima o potrošnji
    private val _displayBudget = MutableStateFlow<DisplayBudget?>(null)
    val displayBudget: StateFlow<DisplayBudget?> = _displayBudget.asStateFlow()
    
    // Transakcije relevantne za budžet
    private val _relevantExpenses = MutableStateFlow<List<Expense>>(emptyList())
    val relevantExpenses: StateFlow<List<Expense>> = _relevantExpenses.asStateFlow()
    
    private val _relevantIncomes = MutableStateFlow<List<Income>>(emptyList())
    val relevantIncomes: StateFlow<List<Income>> = _relevantIncomes.asStateFlow()
    
    // UI stanje
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Učitavanje budžeta po ID-u
     */
    fun loadBudget(budgetId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                budgetRepository.getAllBudgets().collectLatest { allBudgets ->
                    val budget = allBudgets.find { it.id == budgetId }
                    if (budget != null) {
                        _currentBudget.value = budget
                        loadRelevantTransactions(budget)
                    } else {
                        _errorMessage.value = "Budžet nije pronađen"
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(tag, "Greška pri učitavanju budžeta", e)
                _errorMessage.value = "Greška pri učitavanju budžeta: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Učitava transakcije relevantne za budžet
     */
    private fun loadRelevantTransactions(budget: Budget) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                when (budget.type) {
                    BudgetType.EXPENSE -> {
                        // Učitavamo troškove za budžet
                        if (budget.accountId.isNotEmpty()) {
                            // Troškovi za specifičan račun
                            expenseRepository.getExpensesForAccount(budget.accountId).collectLatest { expenses ->
                                processExpensesForBudget(budget, expenses)
                            }
                        } else {
                            // Svi troškovi
                            expenseRepository.getAllExpenses().collectLatest { expenses ->
                                processExpensesForBudget(budget, expenses)
                            }
                        }
                    }
                    BudgetType.INCOME -> {
                        // Učitavamo prihode za budžet
                        if (budget.accountId.isNotEmpty()) {
                            // Prihodi za specifičan račun
                            incomeRepository.getIncomesForAccount(budget.accountId).collectLatest { incomes ->
                                processIncomesForBudget(budget, incomes)
                            }
                        } else {
                            // Svi prihodi
                            incomeRepository.getAllIncomes().collectLatest { incomes ->
                                processIncomesForBudget(budget, incomes)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Greška pri učitavanju transakcija", e)
                _errorMessage.value = "Greška pri učitavanju transakcija: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Obrađuje troškove za budžet i filtrira relevatne
     */
    private fun processExpensesForBudget(budget: Budget, expenses: List<Expense>) {
        val filteredExpenses = expenses.filter { expense ->
            // Filtriramo po vremenskom periodu
            val expenseDate = expense.getDateObject()?.time ?: 0L
            val startDate = budget.getStartDateObject()?.time ?: 0L
            val endDate = budget.getEndDateObject()?.time ?: 0L
            
            val isInTimeRange = expenseDate in startDate..endDate
            
            // Filtriramo po kategorijama ako postoje
            val isInCategory = if (budget.categoryIds.isNotEmpty()) {
                budget.categoryIds.contains(expense.category)
            } else {
                true // Sve kategorije
            }
            
            isInTimeRange && isInCategory
        }
        
        // Ažuriramo relevantne troškove
        _relevantExpenses.value = filteredExpenses
        
        // Računamo ukupan trošak za ovaj budžet
        val totalExpense = filteredExpenses.sumOf { it.amount }
        
        // Kreiramo DisplayBudget sa informacijama o troškovima
        _displayBudget.value = DisplayBudget(
            budget = budget,
            spentAmount = totalExpense
        )
        
        _isLoading.value = false
    }
    
    /**
     * Obrađuje prihode za budžet i filtrira relevatne
     */
    private fun processIncomesForBudget(budget: Budget, incomes: List<Income>) {
        val filteredIncomes = incomes.filter { income ->
            // Filtriramo po vremenskom periodu
            val incomeDate = income.getDateObject()?.time ?: 0L
            val startDate = budget.getStartDateObject()?.time ?: 0L
            val endDate = budget.getEndDateObject()?.time ?: 0L
            
            val isInTimeRange = incomeDate in startDate..endDate
            
            // Filtriramo po kategorijama ako postoje
            val isInCategory = if (budget.categoryIds.isNotEmpty()) {
                budget.categoryIds.contains(income.category)
            } else {
                true // Sve kategorije
            }
            
            isInTimeRange && isInCategory
        }
        
        // Ažuriramo relevantne prihode
        _relevantIncomes.value = filteredIncomes
        
        // Računamo ukupan prihod za ovaj budžet
        val totalIncome = filteredIncomes.sumOf { it.amount }
        
        // Kreiramo DisplayBudget sa informacijama o prihodima
        _displayBudget.value = DisplayBudget(
            budget = budget,
            spentAmount = totalIncome
        )
        
        _isLoading.value = false
    }
    
    /**
     * Factory za kreiranje BudgetDetailViewModel
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetDetailViewModel::class.java)) {
                val firestore = FirebaseFirestore.getInstance()
                val auth = FirebaseAuth.getInstance()
                
                // Dobijamo instance preko RepositoryManager-a
                val expenseRepo = RepositoryManager.getExpenseRepositoryForBudget()
                val incomeRepo = RepositoryManager.getIncomeRepositoryForBudget()
                
                return BudgetDetailViewModel(
                    budgetRepository = BudgetRepository(firestore, auth),
                    expenseRepository = expenseRepo,
                    incomeRepository = incomeRepo
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}