package com.petar.smrdici.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ExpenseRepository private constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    
    // Референца на AccountRepository
    private var accountRepository: AccountRepository? = null
    
    fun setAccountRepository(accountRepo: AccountRepository) {
        this.accountRepository = accountRepo
    }
    
    // Shared expenses collection
    private val sharedExpensesCollection
        get() = firestore.collection("shared_expenses")
    
    // Ажурирање постојећег расхода
    suspend fun updateExpense(expense: Expense): Result<Expense> {
        return try {
            LogUtils.i("ExpenseRepository", "Ažuriram rashod: $expense", category = "expense")
            
            // Проверавамо да ли је ID валидан
            if (expense.id.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID расхода"))
            }
            
            // Прво налазимо стари расход да бисмо добили стари износ
            val oldExpenseDoc = sharedExpensesCollection.document(expense.id).get().await()
            val oldExpense = oldExpenseDoc.toObject(Expense::class.java)
            
            // Осигурамо да имамо валидан датум у формату "YYYY-MM-DD"
            val validDate = if (expense.date.isEmpty() || !expense.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                dateFormat.format(Date())
            } else {
                expense.date
            }
            
            val expenseToUpdate = expense.copy(date = validDate)
            
            // Pretvaramo validDate string u Date objekat, pa u Timestamp za Firebase
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val dateObject = dateFormat.parse(validDate) ?: Date()
            
            // Kreiramo mapu podataka koja će biti sačuvana u Firestore
            val expenseMap = mapOf(
                "id" to expenseToUpdate.id,
                "amount" to expenseToUpdate.amount,
                "description" to expenseToUpdate.description,
                "category" to expenseToUpdate.category,
                "date" to Timestamp(dateObject),
                "accountId" to expenseToUpdate.accountId
            )
            
            // Ажурирамо расход у бази података (shared collection)
            sharedExpensesCollection.document(expenseToUpdate.id).set(expenseMap).await()
            
            // Ажурирамо баланс рачуна
            if (oldExpense != null) {
                // Враћамо стари износ (повећавамо баланс)
                accountRepository?.updateAccountBalance(oldExpense.accountId, oldExpense.amount)
                // Одузимамо нови износ (смањујемо баланс)
                accountRepository?.updateAccountBalance(expenseToUpdate.accountId, -expenseToUpdate.amount)
            }
            
            Result.success(expenseToUpdate)
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Greška pri ažuriranju rashoda", e, category = "expense")
            Result.failure(e)
        }
    }
    
    // Get expense by ID
    suspend fun getExpenseById(expenseId: String): Expense? {
        return try {
            val expenseDoc = sharedExpensesCollection.document(expenseId).get().await()
            if (!expenseDoc.exists()) {
                return null
            }
            
            val data = expenseDoc.data ?: return null
            
            // Extract date from Timestamp
            val timestamp = data["date"] as? Timestamp
            val dateStr = if (timestamp != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                dateFormat.format(timestamp.toDate())
            } else {
                ""
            }
            
            Expense(
                id = expenseDoc.id,
                userId = data["userId"] as? String ?: "",
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                description = data["description"] as? String ?: "",
                category = data["category"] as? String ?: "",
                date = dateStr,
                accountId = data["accountId"] as? String ?: "",
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Error getting expense by ID: $expenseId", e, category = "expense")
            null
        }
    }
    
    companion object {
        @Volatile
        private var instance: ExpenseRepository? = null
        
        fun getInstance(): ExpenseRepository {
            return instance ?: synchronized(this) {
                instance ?: ExpenseRepository().also { instance = it }
            }
        }
    }
} 