package com.petar.smrdici.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.petar.smrdici.data.model.Transaction
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import com.petar.smrdici.utils.LogUtils
import com.petar.smrdici.data.repository.AccountRepository

class TransactionRepository private constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("No authenticated user")

    // User-specific collections
    private val userExpensesCollection
        get() = firestore.collection("users").document(currentUserId).collection("expenses")
    
    private val userIncomesCollection
        get() = firestore.collection("users").document(currentUserId).collection("incomes")
        
    // Reference to AccountRepository - initialize directly instead of using lazy
    private val accountRepository = AccountRepository.getInstance()

    suspend fun addExpense(expense: Expense) {
        userExpensesCollection.document(expense.id).set(expense).await()
        
        // Update account balance (decrease it)
        try {
            LogUtils.i("TransactionRepository", "Updating account balance for new expense: accountId=${expense.accountId}, amount=-${expense.amount}", "transaction")
            accountRepository.updateAccountBalance(expense.accountId, -expense.amount)
        } catch (e: Exception) {
            // Log error but don't fail the transaction
            LogUtils.e("TransactionRepository", "Failed to update account balance for expense", e, "transaction")
        }
    }

    suspend fun addIncome(income: Income) {
        userIncomesCollection.document(income.id).set(income).await()
        
        // Update account balance (increase it)
        try {
            LogUtils.i("TransactionRepository", "Updating account balance for new income: accountId=${income.accountId}, amount=${income.amount}", "transaction")
            accountRepository.updateAccountBalance(income.accountId, income.amount)
        } catch (e: Exception) {
            // Log error but don't fail the transaction
            LogUtils.e("TransactionRepository", "Failed to update account balance for income", e, "transaction")
        }
    }

    suspend fun updateExpense(expense: Expense) {
        try {
            // First get the old expense details to update the account balance
            val oldExpenseDoc = userExpensesCollection.document(expense.id).get().await()
            
            if (oldExpenseDoc.exists()) {
                val oldAccountId = oldExpenseDoc.getString("accountId") ?: ""
                val oldAmount = oldExpenseDoc.getDouble("amount") ?: 0.0
                
                // Only adjust balance if we have valid old data
                if (oldAccountId.isNotEmpty() && oldAmount > 0) {
                    // Revert the old expense amount (add it back to the account)
                    LogUtils.i("TransactionRepository", "Reverting old expense: accountId=$oldAccountId, amount=$oldAmount", "transaction")
                    accountRepository.updateAccountBalance(oldAccountId, oldAmount)
                }
            }
            
            // Now update the document
            userExpensesCollection.document(expense.id).set(expense).await()
            
            // Apply the new expense amount (subtract from the account)
            LogUtils.i("TransactionRepository", "Applying updated expense: accountId=${expense.accountId}, amount=-${expense.amount}", "transaction")
            accountRepository.updateAccountBalance(expense.accountId, -expense.amount)
            
        } catch (e: Exception) {
            // If there's an error, just update the document without balance changes
            LogUtils.e("TransactionRepository", "Error updating expense with balance adjustment", e, "transaction")
            userExpensesCollection.document(expense.id).set(expense).await()
        }
    }

    suspend fun updateIncome(income: Income) {
        try {
            // First get the old income details to update the account balance
            val oldIncomeDoc = userIncomesCollection.document(income.id).get().await()
            
            if (oldIncomeDoc.exists()) {
                val oldAccountId = oldIncomeDoc.getString("accountId") ?: ""
                val oldAmount = oldIncomeDoc.getDouble("amount") ?: 0.0
                
                // Only adjust balance if we have valid old data
                if (oldAccountId.isNotEmpty() && oldAmount > 0) {
                    // Revert the old income amount (subtract it from the account)
                    LogUtils.i("TransactionRepository", "Reverting old income: accountId=$oldAccountId, amount=-$oldAmount", "transaction")
                    accountRepository.updateAccountBalance(oldAccountId, -oldAmount)
                }
            }
            
            // Now update the document
            userIncomesCollection.document(income.id).set(income).await()
            
            // Apply the new income amount (add to the account)
            LogUtils.i("TransactionRepository", "Applying updated income: accountId=${income.accountId}, amount=${income.amount}", "transaction")
            accountRepository.updateAccountBalance(income.accountId, income.amount)
            
        } catch (e: Exception) {
            // If there's an error, just update the document without balance changes
            LogUtils.e("TransactionRepository", "Error updating income with balance adjustment", e, "transaction")
            userIncomesCollection.document(income.id).set(income).await()
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        try {
            // First get the expense details to update the account balance
            val expenseDoc = userExpensesCollection.document(expenseId).get().await()
            
            if (expenseDoc.exists()) {
                val accountId = expenseDoc.getString("accountId") ?: ""
                val amount = expenseDoc.getDouble("amount") ?: 0.0
                
                if (accountId.isNotEmpty() && amount > 0) {
                    // When deleting an expense, add the amount back to the account
                    LogUtils.i("TransactionRepository", "Restoring balance for deleted expense: accountId=$accountId, amount=$amount", "transaction")
                    accountRepository.updateAccountBalance(accountId, amount)
                }
            }
            
            // Now delete the document
            userExpensesCollection.document(expenseId).delete().await()
            
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error deleting expense with balance adjustment", e, "transaction")
            // Still try to delete the document even if balance update fails
            userExpensesCollection.document(expenseId).delete().await()
        }
    }

    suspend fun deleteIncome(incomeId: String) {
        try {
            // First get the income details to update the account balance
            val incomeDoc = userIncomesCollection.document(incomeId).get().await()
            
            if (incomeDoc.exists()) {
                val accountId = incomeDoc.getString("accountId") ?: ""
                val amount = incomeDoc.getDouble("amount") ?: 0.0
                
                if (accountId.isNotEmpty() && amount > 0) {
                    // When deleting an income, subtract the amount from the account
                    LogUtils.i("TransactionRepository", "Adjusting balance for deleted income: accountId=$accountId, amount=-$amount", "transaction")
                    accountRepository.updateAccountBalance(accountId, -amount)
                }
            }
            
            // Now delete the document
            userIncomesCollection.document(incomeId).delete().await()
            
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error deleting income with balance adjustment", e, "transaction")
            // Still try to delete the document even if balance update fails
            userIncomesCollection.document(incomeId).delete().await()
        }
    }

    fun getExpenses(): Flow<List<Expense>> = flow {
        val snapshot = userExpensesCollection.get().await()
        val expenses = snapshot.documents.mapNotNull { doc ->
            try {
                val id = doc.id
                val userId = doc.getString("userId") ?: currentUserId
                val amount = doc.getDouble("amount") ?: 0.0
                val description = doc.getString("description") ?: ""
                val category = doc.getString("category") ?: ""
                val accountId = doc.getString("accountId") ?: ""
                
                // Get date from document
                val dateField = doc.get("date")
                val date = when (dateField) {
                    is com.google.firebase.Timestamp -> {
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        dateFormat.format(dateField.toDate())
                    }
                    is String -> dateField
                    else -> {
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        dateFormat.format(java.util.Date())
                    }
                }
                
                Expense(
                    id = id,
                    userId = userId,
                    amount = amount,
                    date = date,
                    accountId = accountId,
                    description = description,
                    category = category
                )
            } catch (e: Exception) {
                null
            }
        }
        emit(expenses)
    }

    fun getIncomes(): Flow<List<Income>> = flow {
        val snapshot = userIncomesCollection.get().await()
        val incomes = snapshot.documents.mapNotNull { doc ->
            try {
                val id = doc.id
                val userId = doc.getString("userId") ?: currentUserId
                val amount = doc.getDouble("amount") ?: 0.0
                val description = doc.getString("description") ?: ""
                val category = doc.getString("category") ?: ""
                val accountId = doc.getString("accountId") ?: ""
                
                // Get date from document
                val dateField = doc.get("date")
                val date = when (dateField) {
                    is com.google.firebase.Timestamp -> {
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        dateFormat.format(dateField.toDate())
                    }
                    is String -> dateField
                    else -> {
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        dateFormat.format(java.util.Date())
                    }
                }
                
                Income(
                    id = id,
                    userId = userId,
                    amount = amount,
                    date = date,
                    accountId = accountId,
                    description = description,
                    category = category
                )
            } catch (e: Exception) {
                null
            }
        }
        emit(incomes)
    }

    fun getExpenseById(expenseId: String): Flow<Expense?> = flow {
        val doc = userExpensesCollection.document(expenseId).get().await()
        
        if (!doc.exists()) {
            emit(null)
            return@flow
        }
        
        val id = doc.id
        val userId = doc.getString("userId") ?: currentUserId
        val amount = doc.getDouble("amount") ?: 0.0
        val description = doc.getString("description") ?: ""
        val category = doc.getString("category") ?: ""
        val accountId = doc.getString("accountId") ?: ""
        
        // Get date from document
        val dateField = doc.get("date")
        val date = when (dateField) {
            is com.google.firebase.Timestamp -> {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(dateField.toDate())
            }
            is String -> dateField
            else -> {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(java.util.Date())
            }
        }
        
        val expense = Expense(
            id = id,
            userId = userId,
            amount = amount,
            date = date,
            accountId = accountId,
            description = description,
            category = category
        )
        
        emit(expense)
    }.catch { e ->
        LogUtils.e("TransactionRepository", "Error getting expense by ID: $expenseId", e, "transaction")
        emit(null)
    }

    fun getIncomeById(incomeId: String): Flow<Income?> = flow {
        val doc = userIncomesCollection.document(incomeId).get().await()
        
        if (!doc.exists()) {
            emit(null)
            return@flow
        }
        
        val id = doc.id
        val userId = doc.getString("userId") ?: currentUserId
        val amount = doc.getDouble("amount") ?: 0.0
        val description = doc.getString("description") ?: ""
        val category = doc.getString("category") ?: ""
        val accountId = doc.getString("accountId") ?: ""
        
        // Get date from document
        val dateField = doc.get("date")
        val date = when (dateField) {
            is com.google.firebase.Timestamp -> {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(dateField.toDate())
            }
            is String -> dateField
            else -> {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(java.util.Date())
            }
        }
        
        val income = Income(
            id = id,
            userId = userId,
            amount = amount,
            date = date,
            accountId = accountId,
            description = description,
            category = category
        )
        
        emit(income)
    }.catch { e ->
        LogUtils.e("TransactionRepository", "Error getting income by ID: $incomeId", e, "transaction")
        emit(null)
    }

    // Direct suspend function to get expense by ID without using Flow
    suspend fun getExpenseByIdDirect(expenseId: String): Expense? {
        return try {
            val doc = userExpensesCollection.document(expenseId).get().await()
            
            if (!doc.exists()) {
                return null
            }
            
            val id = doc.id
            val userId = doc.getString("userId") ?: currentUserId
            val amount = doc.getDouble("amount") ?: 0.0
            val description = doc.getString("description") ?: ""
            val category = doc.getString("category") ?: ""
            val accountId = doc.getString("accountId") ?: ""
            
            // Get date from document
            val dateField = doc.get("date")
            val date = when (dateField) {
                is com.google.firebase.Timestamp -> {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(dateField.toDate())
                }
                is String -> dateField
                else -> {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(java.util.Date())
                }
            }
            
            Expense(
                id = id,
                userId = userId,
                amount = amount,
                date = date,
                accountId = accountId,
                description = description,
                category = category
            )
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error getting expense by ID directly: $expenseId", e, "transaction")
            null
        }
    }

    // Direct suspend function to get income by ID without using Flow
    suspend fun getIncomeByIdDirect(incomeId: String): Income? {
        return try {
            val doc = userIncomesCollection.document(incomeId).get().await()
            
            if (!doc.exists()) {
                return null
            }
            
            val id = doc.id
            val userId = doc.getString("userId") ?: currentUserId
            val amount = doc.getDouble("amount") ?: 0.0
            val description = doc.getString("description") ?: ""
            val category = doc.getString("category") ?: ""
            val accountId = doc.getString("accountId") ?: ""
            
            // Get date from document
            val dateField = doc.get("date")
            val date = when (dateField) {
                is com.google.firebase.Timestamp -> {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(dateField.toDate())
                }
                is String -> dateField
                else -> {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(java.util.Date())
                }
            }
            
            Income(
                id = id,
                userId = userId,
                amount = amount,
                date = date,
                accountId = accountId,
                description = description,
                category = category
            )
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error getting income by ID directly: $incomeId", e, "transaction")
            null
        }
    }

    companion object {
        @Volatile
        private var instance: TransactionRepository? = null

        fun getInstance(): TransactionRepository {
            return instance ?: synchronized(this) {
                instance ?: TransactionRepository().also { instance = it }
            }
        }
    }
} 