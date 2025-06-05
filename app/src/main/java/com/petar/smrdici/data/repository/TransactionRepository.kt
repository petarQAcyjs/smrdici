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

    // Single transactions collection
    private val transactionsCollection
        get() = firestore.collection("transactions")
        
    // Reference to AccountRepository - initialize directly instead of using lazy
    private val accountRepository = AccountRepository.getInstance()

    suspend fun addExpense(expense: Expense) {
        // Add type field to distinguish as expense
        val expenseData = expense.toMap().toMutableMap()
        expenseData["type"] = "EXPENSE"
        
        transactionsCollection.document(expense.id).set(expenseData).await()
        
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
        // Add type field to distinguish as income
        val incomeData = income.toMap().toMutableMap()
        incomeData["type"] = "INCOME"
        
        transactionsCollection.document(income.id).set(incomeData).await()
        
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
            val oldExpenseDoc = transactionsCollection.document(expense.id).get().await()
            
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
            
            // Add type field to distinguish as expense
            val expenseData = expense.toMap().toMutableMap()
            expenseData["type"] = "EXPENSE"
            
            // Now update the document
            transactionsCollection.document(expense.id).set(expenseData).await()
            
            // Apply the new expense amount (subtract from the account)
            LogUtils.i("TransactionRepository", "Applying updated expense: accountId=${expense.accountId}, amount=-${expense.amount}", "transaction")
            accountRepository.updateAccountBalance(expense.accountId, -expense.amount)
            
        } catch (e: Exception) {
            // If there's an error, just update the document without balance changes
            LogUtils.e("TransactionRepository", "Error updating expense with balance adjustment", e, "transaction")
            
            // Add type field to distinguish as expense
            val expenseData = expense.toMap().toMutableMap()
            expenseData["type"] = "EXPENSE"
            
            transactionsCollection.document(expense.id).set(expenseData).await()
        }
    }

    suspend fun updateIncome(income: Income) {
        try {
            // First get the old income details to update the account balance
            val oldIncomeDoc = transactionsCollection.document(income.id).get().await()
            
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
            
            // Add type field to distinguish as income
            val incomeData = income.toMap().toMutableMap()
            incomeData["type"] = "INCOME"
            
            // Now update the document
            transactionsCollection.document(income.id).set(incomeData).await()
            
            // Apply the new income amount (add to the account)
            LogUtils.i("TransactionRepository", "Applying updated income: accountId=${income.accountId}, amount=${income.amount}", "transaction")
            accountRepository.updateAccountBalance(income.accountId, income.amount)
            
        } catch (e: Exception) {
            // If there's an error, just update the document without balance changes
            LogUtils.e("TransactionRepository", "Error updating income with balance adjustment", e, "transaction")
            
            // Add type field to distinguish as income
            val incomeData = income.toMap().toMutableMap()
            incomeData["type"] = "INCOME"
            
            transactionsCollection.document(income.id).set(incomeData).await()
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        try {
            // First get the expense details to update the account balance
            val expenseDoc = transactionsCollection.document(expenseId).get().await()
            
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
            transactionsCollection.document(expenseId).delete().await()
            
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error deleting expense with balance adjustment", e, "transaction")
            // Still try to delete the document even if balance update fails
            transactionsCollection.document(expenseId).delete().await()
        }
    }

    suspend fun deleteIncome(incomeId: String) {
        try {
            // First get the income details to update the account balance
            val incomeDoc = transactionsCollection.document(incomeId).get().await()
            
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
            transactionsCollection.document(incomeId).delete().await()
            
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error deleting income with balance adjustment", e, "transaction")
            // Still try to delete the document even if balance update fails
            transactionsCollection.document(incomeId).delete().await()
        }
    }

    fun getExpenses(): Flow<List<Expense>> = flow {
        val snapshot = transactionsCollection.whereEqualTo("type", "EXPENSE").get().await()
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
                    description = description,
                    category = category,
                    date = date,
                    accountId = accountId
                )
            } catch (e: Exception) {
                LogUtils.e("TransactionRepository", "Error parsing expense document", e, "transaction")
                null
            }
        }
        emit(expenses)
    }.catch { e ->
        LogUtils.e("TransactionRepository", "Error getting expenses", e, "transaction")
        emit(emptyList())
    }

    fun getIncomes(): Flow<List<Income>> = flow {
        val snapshot = transactionsCollection.whereEqualTo("type", "INCOME").get().await()
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
                    description = description,
                    category = category,
                    date = date,
                    accountId = accountId
                )
            } catch (e: Exception) {
                LogUtils.e("TransactionRepository", "Error parsing income document", e, "transaction")
                null
            }
        }
        emit(incomes)
    }.catch { e ->
        LogUtils.e("TransactionRepository", "Error getting incomes", e, "transaction")
        emit(emptyList())
    }

    // Helper method to convert Expense to Map
    private fun Expense.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "amount" to amount,
            "description" to description,
            "category" to category,
            "date" to date,
            "accountId" to accountId
        )
    }
    
    // Helper method to convert Income to Map
    private fun Income.toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "amount" to amount,
            "description" to description,
            "category" to category,
            "date" to date,
            "accountId" to accountId
        )
    }

    fun getExpenseById(expenseId: String): Flow<Expense?> = flow {
        val doc = transactionsCollection.document(expenseId).get().await()
        
        if (!doc.exists() || doc.getString("type") != "EXPENSE") {
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
            description = description,
            category = category,
            date = date,
            accountId = accountId
        )
        
        emit(expense)
    }.catch { e ->
        LogUtils.e("TransactionRepository", "Error getting expense by ID: $expenseId", e, "transaction")
        emit(null)
    }

    fun getIncomeById(incomeId: String): Flow<Income?> = flow {
        val doc = transactionsCollection.document(incomeId).get().await()
        
        if (!doc.exists() || doc.getString("type") != "INCOME") {
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
            description = description,
            category = category,
            date = date,
            accountId = accountId
        )
        
        emit(income)
    }.catch { e ->
        LogUtils.e("TransactionRepository", "Error getting income by ID: $incomeId", e, "transaction")
        emit(null)
    }

    // Direct suspend function to get expense by ID without using Flow
    suspend fun getExpenseByIdDirect(expenseId: String): Expense? {
        return try {
            val doc = transactionsCollection.document(expenseId).get().await()
            
            if (!doc.exists() || doc.getString("type") != "EXPENSE") {
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
                description = description,
                category = category,
                date = date,
                accountId = accountId
            )
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error getting expense by ID directly: $expenseId", e, "transaction")
            null
        }
    }

    // Direct suspend function to get income by ID without using Flow
    suspend fun getIncomeByIdDirect(incomeId: String): Income? {
        return try {
            val doc = transactionsCollection.document(incomeId).get().await()
            
            if (!doc.exists() || doc.getString("type") != "INCOME") {
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
                description = description,
                category = category,
                date = date,
                accountId = accountId
            )
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error getting income by ID directly: $incomeId", e, "transaction")
            null
        }
    }

    // Method to get all transactions (both income and expense)
    fun getAllTransactions(): Flow<List<Transaction>> = flow {
        try {
            LogUtils.d("TransactionRepository", "Getting all transactions from unified collection", "transaction")
            val snapshot = transactionsCollection.get().await()
            
            val transactions = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val userId = doc.getString("userId") ?: currentUserId
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val accountId = doc.getString("accountId") ?: ""
                    val type = doc.getString("type") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    
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
                    
                    when (type) {
                        "INCOME" -> Income(
                            id = id,
                            userId = userId,
                            amount = amount,
                            description = description,
                            category = category,
                            date = date,
                            accountId = accountId,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                        "EXPENSE" -> Expense(
                            id = id,
                            userId = userId,
                            amount = amount,
                            description = description,
                            category = category,
                            date = date,
                            accountId = accountId,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                        else -> null
                    }
                } catch (e: Exception) {
                    LogUtils.e("TransactionRepository", "Error parsing transaction document", e, "transaction")
                    null
                }
            }
            
            LogUtils.i("TransactionRepository", "Retrieved ${transactions.size} transactions from unified collection", "transaction")
            emit(transactions)
        } catch (e: Exception) {
            LogUtils.e("TransactionRepository", "Error getting all transactions", e, "transaction")
            emit(emptyList())
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