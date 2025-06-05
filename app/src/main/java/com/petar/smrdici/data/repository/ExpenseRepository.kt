package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExpenseRepository private constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    
    // Референца на AccountRepository
    private var accountRepository: AccountRepository? = null
    
    fun setAccountRepository(accountRepo: AccountRepository) {
        this.accountRepository = accountRepo
    }
    
    // Добијање тренутног корисника
    private val currentUserId: String?
        get() = auth.currentUser?.uid
    
    // Shared expenses collection
    private val sharedExpensesCollection
        get() = firestore.collection("shared_expenses")
    
    // Legacy: Добијање колекције расхода за тренутног корисника
    private val userExpensesCollection
        get() = currentUserId?.let { uid ->
            firestore.collection("users").document(uid).collection("expenses")
        }
    
    // Helper function to convert LocalDate to Timestamp
    private fun localDateToTimestamp(date: LocalDate): Timestamp {
        return Timestamp(date.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond, 0)
    }
    
    // Helper function to convert LocalDateTime to Timestamp
    private fun localDateTimeToTimestamp(dateTime: LocalDateTime): Timestamp {
        return Timestamp(dateTime.atZone(ZoneId.systemDefault()).toInstant().epochSecond, 0)
    }
    
    // Додавање новог расхода
    suspend fun addExpense(expense: Expense, updateAccountBalance: Boolean = true): Result<Expense> {
        return try {
            if (currentUserId == null) {
                LogUtils.d("ExpenseRepository", "Корисник није пријављен, не могу додати расход", category = "expense")
                return Result.failure(IllegalStateException("Корисник није пријављен"))
            }
            
            LogUtils.i("ExpenseRepository", "Dodajem rashod: $expense", category = "expense")
            
            // Генеришемо ID ако није већ постављен
            val expenseId = expense.id.ifEmpty { UUID.randomUUID().toString() }
            
            // Осигурамо да имамо валидан датум у формату "YYYY-MM-DD"
            val validDate = if (expense.date.isEmpty() || !expense.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(Date())
            } else {
                expense.date
            }
            
            val expenseToAdd = expense.copy(id = expenseId, date = validDate)
            
            // Pretvaramo validDate string u Date objekat, pa u Timestamp za Firebase
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val dateObject = dateFormat.parse(validDate) ?: Date()
            
            // Kreiramo mapu podataka koja će biti sačuvana u Firestore
            val expenseMap = mapOf(
                "id" to expenseId,
                "userId" to expense.userId,
                "amount" to expense.amount,
                "description" to expense.description,
                "category" to expense.category,
                "date" to Timestamp(dateObject),
                "accountId" to expense.accountId,
                "createdAt" to expense.createdAt,
                "updatedAt" to expense.updatedAt
            )
            
            // Чувамо расход у бази података (shared collection)
            sharedExpensesCollection.document(expenseId).set(expenseMap).await()
            
            // Ажурирамо баланс рачуна (смањујемо га) само ако је затражено
            if (updateAccountBalance) {
                accountRepository?.updateAccountBalance(expense.accountId, -expense.amount)
            }
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(expenseToAdd)
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Грешка при додавању расхода", e, category = "expense")
            Result.failure(e)
        }
    }
    
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
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(Date())
            } else {
                expense.date
            }
            
            val expenseToUpdate = expense.copy(date = validDate)
            
            // Pretvaramo validDate string u Date objekat, pa u Timestamp za Firebase
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
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
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(expenseToUpdate)
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Greška pri ažuriranju rashoda", e, category = "expense")
            Result.failure(e)
        }
    }
    
    // Брисање расхода
    suspend fun deleteExpense(expenseId: String): Result<Unit> {
        return try {
            LogUtils.i("ExpenseRepository", "Brišem rashod: $expenseId", category = "expense")
            
            // Проверавамо да ли је ID валидан
            if (expenseId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID расхода"))
            }
            
            // Прво налазимо расход да бисмо добили износ и ID рачуна
            val expenseDoc = sharedExpensesCollection.document(expenseId).get().await()
            
            // Instead of using toObject which requires a no-arg constructor, manually extract the fields
            val accountId = expenseDoc.getString("accountId") ?: ""
            val amount = expenseDoc.getDouble("amount") ?: 0.0
            
            // Only proceed if we have valid data
            if (accountId.isNotEmpty() && amount > 0) {
                // Бришемо расход из базе података (shared collection)
                sharedExpensesCollection.document(expenseId).delete().await()
                
                // Враћамо баланс рачуна (повећавамо га)
                accountRepository?.updateAccountBalance(accountId, amount)
                
                // Ажурирамо локални кеш
                refreshExpenses()
                
                Result.success(Unit)
            } else {
                LogUtils.e("ExpenseRepository", "Nedostaju podaci o rashodu za brisanje: accountId=$accountId, amount=$amount", category = "expense")
                Result.failure(IllegalStateException("Nedostaju podaci o rashodu"))
            }
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Greška pri brisanju rashoda", e, category = "expense")
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
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
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
    
    // Добијање свих расхода
    fun getAllExpenses(): Flow<List<Expense>> = callbackFlow {
        val listenerRegistration = sharedExpensesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("ExpenseRepository", "Greška pri dobavljanju rashoda", error, category = "expense")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Manually map document to Expense object
                        val data = doc.data ?: return@mapNotNull null
                        
                        // Extract date from Timestamp
                        val timestamp = data["date"] as? Timestamp
                        val dateStr = if (timestamp != null) {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(timestamp.toDate())
                        } else {
                            ""
                        }
                        
                        Expense(
                            id = doc.id,
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
                        LogUtils.e("ExpenseRepository", "Greška pri mapiranju dokumenta u Expense", e, category = "expense")
                        null
                    }
                } ?: emptyList()
                
                trySend(expenses)
            }
        
        awaitClose {
            listenerRegistration.remove()
        }
    }
    
    // Добијање расхода за одређени месец
    fun getExpensesForMonth(year: Int, month: Int): Flow<List<Expense>> = callbackFlow {
        // Креирамо датуме за почетак и крај месеца
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1)
        
        // Конвертујемо у Timestamp за Firestore
        val startTimestamp = localDateToTimestamp(startDate)
        val endTimestamp = localDateToTimestamp(endDate)
        
        val listenerRegistration = sharedExpensesCollection
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThan("date", endTimestamp)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("ExpenseRepository", "Greška pri dobavljanju rashoda za mesec", error, category = "expense")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Manually map document to Expense object
                        val data = doc.data ?: return@mapNotNull null
                        
                        // Extract date from Timestamp
                        val timestamp = data["date"] as? Timestamp
                        val dateStr = if (timestamp != null) {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(timestamp.toDate())
                        } else {
                            ""
                        }
                        
                        Expense(
                            id = doc.id,
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
                        LogUtils.e("ExpenseRepository", "Greška pri mapiranju dokumenta u Expense", e, category = "expense")
                        null
                    }
                } ?: emptyList()
                
                trySend(expenses)
            }
        
        awaitClose {
            listenerRegistration.remove()
        }
    }
    
    // Добијање расхода за одређени дан
    fun getExpensesForDay(year: Int, month: Int, day: Int): Flow<List<Expense>> = callbackFlow {
        // Креирамо датум
        val date = LocalDate.of(year, month, day)
        
        // Конвертујемо у Timestamp за Firestore
        val startTimestamp = localDateToTimestamp(date)
        val endTimestamp = localDateToTimestamp(date.plusDays(1))
        
        val listenerRegistration = sharedExpensesCollection
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThan("date", endTimestamp)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("ExpenseRepository", "Greška pri dobavljanju rashoda za dan", error, category = "expense")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Manually map document to Expense object
                        val data = doc.data ?: return@mapNotNull null
                        
                        // Extract date from Timestamp
                        val timestamp = data["date"] as? Timestamp
                        val dateStr = if (timestamp != null) {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(timestamp.toDate())
                        } else {
                            ""
                        }
                        
                        Expense(
                            id = doc.id,
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
                        LogUtils.e("ExpenseRepository", "Greška pri mapiranju dokumenta u Expense", e, category = "expense")
                        null
                    }
                } ?: emptyList()
                
                trySend(expenses)
            }
        
        awaitClose {
            listenerRegistration.remove()
        }
    }
    
    // Освежавање локалног кеша
    private fun refreshExpenses() {
        // Имплементација ће бити додата касније
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