package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
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
    suspend fun addExpense(expense: Expense): Result<Expense> {
        return try {
            Log.d("ExpenseRepository", "Додајем расход: $expense")
            
            // Генеришемо ID ако није већ постављен
            val expenseId = expense.id.ifEmpty { UUID.randomUUID().toString() }
            val expenseToAdd = expense.copy(id = expenseId)
            
            // Чувамо расход у бази података
            userExpensesCollection.document(expenseId).set(expenseToAdd).await()
            
            // Ажурирамо баланс рачуна (смањујемо га)
            accountRepository?.updateAccountBalance(expense.accountId, -expense.amount)
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(expenseToAdd)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при додавању расхода", e)
            Result.failure(e)
        }
    }
    
    // Ажурирање расхода
    suspend fun updateExpense(expense: Expense): Result<Expense> {
        return try {
            Log.d("ExpenseRepository", "Ажурирам расход: ${expense.id}")
            
            // Проверавамо да ли расход има валидан ID
            if (expense.id.isEmpty()) {
                return Result.failure(IllegalArgumentException("Расход нема валидан ID"))
            }
            
            // Чувамо расход у бази података
            userExpensesCollection.document(expense.id).set(expense).await()
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(expense)
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при ажурирању расхода", e)
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
    
    // Добијање једног расхода по ID-у
    suspend fun getExpenseById(expenseId: String): Result<Expense> {
        return try {
            Log.d("ExpenseRepository", "Учитавам расход: $expenseId")
            
            // Проверавамо да ли је ID валидан
            if (expenseId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID расхода"))
            }
            
            // Учитавамо расход из базе података
            val docSnapshot = userExpensesCollection.document(expenseId).get().await()
            
            if (docSnapshot.exists()) {
                val expense = docSnapshot.toObject(Expense::class.java)
                    ?: return Result.failure(IllegalStateException("Не могу да претворим документ у Expense објекат"))
                
                Result.success(expense)
            } else {
                Result.failure(NoSuchElementException("Расход није пронађен"))
            }
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Грешка при учитавању расхода", e)
            Result.failure(e)
        }
    }
    
    // Добијање свих расхода за тренутног корисника
    fun getAllExpenses(): Flow<List<Expense>> = callbackFlow {
        Log.d("ExpenseRepository", "Учитавам све расходе")
        
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
                        doc.toObject(Expense::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("ExpenseRepository", "Учитано ${expenses.size} расхода")
                
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
                    doc.toObject(Expense::class.java)?.copy(id = doc.id)
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
        Log.d("ExpenseRepository", "Учитавам трошкове за период од $startDate до $endDate")
        
        val listener = userExpensesCollection
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExpenseRepository", "Грешка при слушању расхода за период", error)
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com[^\\s]+".toRegex()
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
                        doc.toObject(Expense::class.java)?.copy(id = doc.id)
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
        Log.d("ExpenseRepository", "Учитавам трошкове за рачун: $accountId")
        
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
                            val urlPattern = "https://console\\.firebase\\.google\\.com[^\\s]+".toRegex()
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
                        doc.toObject(Expense::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("ExpenseRepository", "Учитано ${expenses.size} расхода за рачун")
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
    
    companion object {
        @Volatile
        private var instance: ExpenseRepository? = null
        
        fun getInstance(): ExpenseRepository {
            return instance ?: synchronized(this) {
                instance ?: ExpenseRepository().also { instance = it }
            }
        }
        
        fun initialize(context: Context) {
            if (instance == null) {
                instance = ExpenseRepository()
                // Повезујемо са AccountRepository
                instance?.setAccountRepository(AccountRepository.getInstance(context))
            }
        }
    }
} 