package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Expense
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

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
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Добијање колекције расхода за тренутног корисника
    private val userExpensesCollection
        get() = firestore.collection("users").document(currentUserId).collection("expenses")
    
    // Додавање новог расхода
    suspend fun addExpense(expense: Expense, updateAccountBalance: Boolean = true): Result<Expense> {
        return try {
            Log.d("ExpenseRepository", "Додајем расход: $expense")
            
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
                "amount" to expenseToAdd.amount,
                "description" to expenseToAdd.description,
                "category" to expenseToAdd.category,
                "date" to com.google.firebase.Timestamp(dateObject), // Koristimo Timestamp umesto String
                "accountId" to expenseToAdd.accountId
            )
            
            // Чувамо расход у бази података
            userExpensesCollection.document(expenseId).set(expenseMap).await()
            
            // Ажурирамо баланс рачуна (смањујемо га) само ако је затражено
            if (updateAccountBalance) {
                accountRepository?.updateAccountBalance(expense.accountId, -expense.amount)
            }
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(expenseToAdd)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при додавању расхода", e)
            Result.failure(e)
        }
    }
    
    // Брисање расхода
    suspend fun deleteExpense(expenseId: String): Result<Unit> {
        return try {
            Log.d("ExpenseRepository", "Бришем расход: $expenseId")
            
            // Проверавамо да ли је ID валидан
            if (expenseId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID расхода"))
            }
            
            // Прво налазимо расход да бисмо добили износ и ID рачуна
            val expenseDoc = userExpensesCollection.document(expenseId).get().await()
            val expense = expenseDoc.toObject(Expense::class.java)
            
            // Бришемо расход из базе података
            userExpensesCollection.document(expenseId).delete().await()
            
            // Ако смо успешно добавили расход, враћамо баланс рачуна (повећавамо га)
            if (expense != null) {
                accountRepository?.updateAccountBalance(expense.accountId, expense.amount)
            }
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при брисању расхода", e)
            Result.failure(e)
        }
    }
    
    // Добијање свих расхода за тренутног корисника
    fun getAllExpenses(): Flow<List<Expense>> = callbackFlow {
        Log.d("ExpenseRepository", "Учитавам све расходе - BUDGET FIX")
        
        val listener = userExpensesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExpenseRepository", "Грешка при слушању расхода", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Učitavanje datuma - koristimo getTimestamp jer je date Timestamp, a ne String
                        val dateStr = try {
                            val timestamp = doc.getTimestamp("date")
                            if (timestamp != null) {
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(timestamp.toDate())
                            } else {
                                // Ako je timestamp null, koristimo današnji datum
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(Date())
                            }
                        } catch (e: Exception) {
                            // Ako dođe do greške pri čitanju Timestamp-a, pokušavamo čitati kao String (za podršku starijih podataka)
                            val dateFromString = doc.getString("date") ?: ""
                            if (dateFromString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                dateFromString
                            } else {
                                // Ako ni to ne radi, koristimo današnji datum
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(Date())
                            }
                        }
                        
                        val expense = Expense(id, amount, description, category, dateStr, accountId)
                        
                        Log.d("ExpenseRepository", "Учитан трошак ID: ${expense.id}, износ: ${expense.amount}, датум: ${expense.getFormattedDate()}")
                        
                        expense
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("ExpenseRepository", "Учитано ${expenses.size} расхода - BUDGET FIX")
                
                // Детаљнији лог за дебагирање - приказује све учитане расходе
                if (expenses.isNotEmpty()) {
                    Log.d("ExpenseRepository", "Учитани расходи: ${expenses.map { "${it.id} (${it.amount})" }}")
                } else {
                    Log.d("ExpenseRepository", "Нема учитаних расхода. Проверите Firebase конекцију и податке.")
                }
                
                // Ажурирамо локални кеш
                _expenses.value = expenses
                
                // Шаљемо нову листу
                trySend(expenses)
            }
        
        awaitClose { 
            Log.d("ExpenseRepository", "Затварам listener за расходе")
            listener.remove() 
        }
    }
    
    // Освежавање листе расхода (користи се интерно)
    private suspend fun refreshExpenses() {
        try {
            Log.d("ExpenseRepository", "Освежавам листу расхода")
            
            val snapshot = userExpensesCollection
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val expenses = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val accountId = doc.getString("accountId") ?: ""
                    
                    // Učitavanje datuma - koristimo getTimestamp jer je date Timestamp, a ne String
                    val dateStr = try {
                        val timestamp = doc.getTimestamp("date")
                        if (timestamp != null) {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(timestamp.toDate())
                        } else {
                            // Ako je timestamp null, koristimo današnji datum
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(Date())
                        }
                    } catch (e: Exception) {
                        // Ako dođe do greške pri čitanju Timestamp-a, pokušavamo čitati kao String (za podršku starijih podataka)
                        val dateFromString = doc.getString("date") ?: ""
                        if (dateFromString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            dateFromString
                        } else {
                            // Ako ni to ne radi, koristimo današnji datum
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(Date())
                        }
                    }
                    
                    val expense = Expense(id, amount, description, category, dateStr, accountId)
                    
                    Log.d("ExpenseRepository", "Учитан трошак ID: ${expense.id}, износ: ${expense.amount}, датум: ${expense.getFormattedDate()}")
                    
                    expense
                } catch (e: Exception) {
                    Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                    null
                }
            }
            
            Log.d("ExpenseRepository", "Освежено ${expenses.size} расхода")
            
            _expenses.value = expenses
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при освежавању расхода", e)
        }
    }
    
    // Додајемо методу за добијање трошкова за одређени период
    fun getExpensesForPeriod(startDate: Date, endDate: Date): Flow<List<Expense>> = callbackFlow {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        Log.d("ExpenseRepository", "Учитавам трошкове за период од $startDateStr до $endDateStr")
        
        val listener = userExpensesCollection
            .whereGreaterThanOrEqualTo("date", startDateStr)
            .whereLessThanOrEqualTo("date", endDateStr)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExpenseRepository", "Грешка при слушању расхода за период", error)
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                            val matchResult = urlPattern.find(msg)
                            matchResult?.value
                        }
                        
                        Log.e("ExpenseRepository", "Потребно је креирати индекс у Firebase конзоли. " +
                               "Користите следећи линк: $indexUrl")
                        
                        // Шаљемо празну листу уместо да затворимо flow са грешком
                        trySend(emptyList())
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Učitavanje datuma - koristimo getTimestamp jer je date Timestamp, a ne String
                        val dateStr = try {
                            val timestamp = doc.getTimestamp("date")
                            if (timestamp != null) {
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(timestamp.toDate())
                            } else {
                                // Ako je timestamp null, koristimo današnji datum
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(Date())
                            }
                        } catch (e: Exception) {
                            // Ako dođe do greške pri čitanju Timestamp-a, pokušavamo čitati kao String (za podršku starijih podataka)
                            val dateFromString = doc.getString("date") ?: ""
                            if (dateFromString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                dateFromString
                            } else {
                                // Ako ni to ne radi, koristimo današnji datum
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(Date())
                            }
                        }
                        
                        val expense = Expense(id, amount, description, category, dateStr, accountId)
                        
                        Log.d("ExpenseRepository", "Учитан трошак за период ID: ${expense.id}, износ: ${expense.amount}, датум: ${expense.getFormattedDate()}")
                        
                        expense
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("ExpenseRepository", "Учитано ${expenses.size} расхода за период")
                trySend(expenses)
            }
        
        awaitClose { 
            Log.d("ExpenseRepository", "Затварам listener за расходе за период")
            listener.remove() 
        }
    }
    
    // Добијање расхода за одређени рачун
    fun getExpensesForAccount(accountId: String): Flow<List<Expense>> = callbackFlow {
        Log.d("ExpenseRepository", "Учитавам трошкове за рачун: $accountId - BUDGET FIX")
        
        val listener = userExpensesCollection
            .whereEqualTo("accountId", accountId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExpenseRepository", "Грешка при слушању расхода за рачун", error)
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                            val matchResult = urlPattern.find(msg)
                            matchResult?.value
                        }
                        
                        Log.e("ExpenseRepository", "Потребно је креирати индекс у Firebase конзоли. " +
                               "Користите следећи линк: $indexUrl")
                        
                        // Шаљемо празну листу уместо да затворимо flow са грешком
                        trySend(emptyList())
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Učitavanje datuma - koristimo getTimestamp jer je date Timestamp, a ne String
                        val dateStr = try {
                            val timestamp = doc.getTimestamp("date")
                            if (timestamp != null) {
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(timestamp.toDate())
                            } else {
                                // Ako je timestamp null, koristimo današnji datum
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(Date())
                            }
                        } catch (e: Exception) {
                            // Ako dođe do greške pri čitanju Timestamp-a, pokušavamo čitati kao String (za podršku starijih podataka)
                            val dateFromString = doc.getString("date") ?: ""
                            if (dateFromString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                dateFromString
                            } else {
                                // Ako ni to ne radi, koristimo današnji datum
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                dateFormat.format(Date())
                            }
                        }
                        
                        val expense = Expense(id, amount, description, category, dateStr, accountId)
                        
                        Log.d("ExpenseRepository", "Учитан трошак ID: ${expense.id}, износ: ${expense.amount}, датум: ${expense.getFormattedDate()}")
                        
                        expense
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("ExpenseRepository", "Учитано ${expenses.size} расхода за рачун - BUDGET FIX")
                
                // Детаљнији лог за дебагирање
                if (expenses.isNotEmpty()) {
                    Log.d("ExpenseRepository", "Учитани расходи за рачун: ${expenses.map { "${it.id} (${it.amount})" }}")
                } else {
                    Log.d("ExpenseRepository", "Нема учитаних расхода за рачун $accountId.")
                }
                
                trySend(expenses)
            }
        
        awaitClose { 
            Log.d("ExpenseRepository", "Затварам listener за расходе за рачун")
            listener.remove() 
        }
    }
    
    // Добијање свих расхода као Flow за observovanje
    fun getExpenses(): Flow<List<Expense>> {
        return _expenses.asStateFlow()
    }
    
    // Брисање свих расхода (за операцију увоза)
    suspend fun deleteAllExpenses() {
        try {
            Log.d("ExpenseRepository", "Бришем све расходе")
            
            val snapshot = userExpensesCollection.get().await()
            
            val batch = firestore.batch()
            for (document in snapshot.documents) {
                batch.delete(userExpensesCollection.document(document.id))
            }
            
            batch.commit().await()
            
            // Освежавање локалног кеша
            _expenses.value = emptyList()
            
            Log.d("ExpenseRepository", "Сви расходи су обрисани")
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при брисању свих расхода", e)
        }
    }
    
    // Dobijanje rashoda za određeni račun i period
    fun getExpensesForAccount(accountId: String, startDate: Date, endDate: Date): List<Expense> {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        Log.d("ExpenseRepository", "Učitavam troškove za račun: $accountId i period od $startDateStr do $endDateStr")
        
        try {
            // Filtriramo rashode za račun u datom vremenskom periodu
            val expenses = _expenses.value.filter { expense -> 
                expense.accountId == accountId &&
                expense.date >= startDateStr &&
                expense.date <= endDateStr
            }
            
            Log.d("ExpenseRepository", "Filtrirano ${expenses.size} rashoda za račun i period")
            return expenses
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Greška pri filtriranju rashoda za račun i period", e)
            return emptyList()
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
        
        fun initialize() {
            if (instance == null) {
                instance = ExpenseRepository()
                // Повезујемо са AccountRepository
                instance?.setAccountRepository(AccountRepository.getInstance())
            }
        }
    }
} 