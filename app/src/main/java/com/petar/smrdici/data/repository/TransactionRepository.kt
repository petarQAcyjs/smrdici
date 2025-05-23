package com.petar.smrdici.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.petar.smrdici.data.model.Transaction
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

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

    suspend fun addExpense(expense: Expense) {
        userExpensesCollection.document(expense.id).set(expense).await()
    }

    suspend fun addIncome(income: Income) {
        userIncomesCollection.document(income.id).set(income).await()
    }

    suspend fun updateExpense(expense: Expense) {
        userExpensesCollection.document(expense.id).set(expense).await()
    }

    suspend fun updateIncome(income: Income) {
        userIncomesCollection.document(income.id).set(income).await()
    }

    suspend fun deleteExpense(expenseId: String) {
        userExpensesCollection.document(expenseId).delete().await()
    }

    suspend fun deleteIncome(incomeId: String) {
        userIncomesCollection.document(incomeId).delete().await()
    }

    fun getExpenses(): Flow<List<Expense>> = flow {
        val snapshot = userExpensesCollection.get().await()
        emit(snapshot.documents.mapNotNull { it.toObject(Expense::class.java) })
    }

    fun getIncomes(): Flow<List<Income>> = flow {
        val snapshot = userIncomesCollection.get().await()
        emit(snapshot.documents.mapNotNull { it.toObject(Income::class.java) })
    }

    fun getExpenseById(expenseId: String): Flow<Expense?> = flow {
        val snapshot = userExpensesCollection.document(expenseId).get().await()
        emit(snapshot.toObject(Expense::class.java))
    }

    fun getIncomeById(incomeId: String): Flow<Income?> = flow {
        val snapshot = userIncomesCollection.document(incomeId).get().await()
        emit(snapshot.toObject(Income::class.java))
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