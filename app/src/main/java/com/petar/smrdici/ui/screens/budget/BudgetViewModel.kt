package com.petar.smrdici.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.petar.smrdici.data.model.Transaction
import com.petar.smrdici.data.model.TransactionType
import com.petar.smrdici.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date

class BudgetViewModel : ViewModel() {
    private val repository = TransactionRepository()
    
    private val _uiState = MutableStateFlow<BudgetUiState>(BudgetUiState.Loading)
    val uiState: StateFlow<BudgetUiState> = _uiState
    
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions
    
    // Форма за унос нове трансакције
    private val _transactionFormState = MutableStateFlow(TransactionFormState())
    val transactionFormState: StateFlow<TransactionFormState> = _transactionFormState
    
    init {
        loadTransactions()
    }
    
    private fun loadTransactions() {
        viewModelScope.launch {
            repository.getTransactionsForCurrentUser()
                .catch { e ->
                    _uiState.value = BudgetUiState.Error(e.message ?: "Грешка при учитавању трансакција")
                }
                .collect { transactions ->
                    _transactions.value = transactions
                    calculateBudgetSummary(transactions)
                }
        }
    }
    
    private fun calculateBudgetSummary(transactions: List<Transaction>) {
        val totalIncome = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }
            
        val totalExpense = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
            
        val balance = totalIncome - totalExpense
        
        _uiState.value = BudgetUiState.Success(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = balance,
            transactions = transactions
        )
    }
    
    // Ажурирање форме за унос трансакције
    fun updateTransactionForm(update: (TransactionFormState) -> TransactionFormState) {
        _transactionFormState.value = update(_transactionFormState.value)
    }
    
    // Додавање нове трансакције
    fun addTransaction() {
        viewModelScope.launch {
            val form = _transactionFormState.value
            
            if (!form.isValid) {
                return@launch
            }
            
            val transaction = Transaction(
                amount = form.amount.toDoubleOrNull() ?: 0.0,
                description = form.description,
                category = form.category,
                type = form.type,
                date = Timestamp(Date(form.date))
            )
            
            repository.addTransaction(transaction)
                .onSuccess {
                    // Ресетујемо форму
                    _transactionFormState.value = TransactionFormState()
                }
                .onFailure { e ->
                    _uiState.value = BudgetUiState.Error(e.message ?: "Грешка при додавању трансакције")
                }
        }
    }
    
    // Брисање трансакције
    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
                .onFailure { e ->
                    _uiState.value = BudgetUiState.Error(e.message ?: "Грешка при брисању трансакције")
                }
        }
    }
}

// Стање корисничког интерфејса
sealed class BudgetUiState {
    object Loading : BudgetUiState()
    data class Success(
        val totalIncome: Double,
        val totalExpense: Double,
        val balance: Double,
        val transactions: List<Transaction>
    ) : BudgetUiState()
    data class Error(val message: String) : BudgetUiState()
}

// Стање форме за унос трансакције
data class TransactionFormState(
    val amount: String = "",
    val description: String = "",
    val category: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val date: Long = System.currentTimeMillis()
) {
    val isValid: Boolean
        get() = amount.isNotBlank() && 
                amount.toDoubleOrNull() != null && 
                description.isNotBlank() && 
                category.isNotBlank()
} 