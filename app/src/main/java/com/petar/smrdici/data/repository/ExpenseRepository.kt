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
    
    // Добијање колекције расхода за тренутног корисника
    private val userExpensesCollection
        get() = currentUserId?.let { uid ->
            firestore.collection("users").document(uid).collection("expenses")
        }
    
    // Додавање новог расхода
    suspend fun addExpense(expense: Expense, updateAccountBalance: Boolean = true): Result<Expense> {
        return try {
            if (currentUserId == null) {
                LogUtils.d("ExpenseRepository", "Корисник није пријављен, не могу додати расход", category = "expense")
                return Result.failure(IllegalStateException("Корисник није пријављен"))
            }
            
            val collection = userExpensesCollection ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            
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
                "date" to com.google.firebase.Timestamp(dateObject),
                "accountId" to expense.accountId,
                "createdAt" to expense.createdAt,
                "updatedAt" to expense.updatedAt
            )
            
            // Чувамо расход у бази података
            collection.document(expenseId).set(expenseMap).await()
            
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
            val oldExpenseDoc = userExpensesCollection?.document(expense.id)?.get()?.await()
            val oldExpense = oldExpenseDoc?.toObject(Expense::class.java)
            
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
                "date" to com.google.firebase.Timestamp(dateObject),
                "accountId" to expenseToUpdate.accountId
            )
            
            // Ажурирамо расход у бази података
            userExpensesCollection?.document(expenseToUpdate.id)?.set(expenseMap)?.await()
            
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
            val expenseDoc = userExpensesCollection?.document(expenseId)?.get()?.await()
            val expense = expenseDoc?.toObject(Expense::class.java)
            
            // Бришемо расход из базе података
            userExpensesCollection?.document(expenseId)?.delete()?.await()
            
            // Ако смо успешно добавили расход, враћамо баланс рачуна (повећавамо га)
            if (expense != null) {
                accountRepository?.updateAccountBalance(expense.accountId, expense.amount)
            }
            
            // Ажурирамо локални кеш
            refreshExpenses()
            
            Result.success(Unit)
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Greška pri brisanju rashoda", e, category = "expense")
            Result.failure(e)
        }
    }
    
    // Добијање свих расхода за тренутног корисника
    fun getAllExpenses(): Flow<List<Expense>> = callbackFlow {
        LogUtils.i("ExpenseRepository", "Учитавам све расходе", category = "expense")
        
        if (currentUserId == null) {
            LogUtils.d("ExpenseRepository", "Корисник није пријављен, враћам празну листу", category = "expense")
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val collection = userExpensesCollection
        if (collection == null) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val listener = collection
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
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Expense(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                // Log samo ukupan broj i statistike umesto pojedinačnih stavki
                if (expenses.isNotEmpty()) {
                    val totalAmount = expenses.sumOf { it.amount }
                    val avgAmount = totalAmount / expenses.size.toDouble()
                    val minAmount = expenses.minOfOrNull { it.amount } ?: 0.0
                    val maxAmount = expenses.maxOfOrNull { it.amount } ?: 0.0
                    
                    LogUtils.i("ExpenseRepository", 
                        "Učitano ${expenses.size} rashoda. " +
                        "Ukupno: $totalAmount, Prosek: $avgAmount, Min: $minAmount, Max: $maxAmount", 
                        category = "expense")
                }
                
                // Ažuriramo lokalni keš
                _expenses.value = expenses
                
                // Šaljemo novu listu
                trySend(expenses)
            }
        
        // IMPORTANT: This ensures the listener is removed when the flow is cancelled
        awaitClose {
            LogUtils.d("ExpenseRepository", "Затварам listener за расходе", category = "expense")
            listener.remove()
        }
    }
    
    // Освежавање листе расхода (користи се интерно)
    private suspend fun refreshExpenses() {
        try {
            LogUtils.d("ExpenseRepository", "Osvežavam listu rashoda", category = "expense")
            
            val snapshot = userExpensesCollection?.get()?.await()
            
            val expenses = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val accountId = doc.getString("accountId") ?: ""
                    
                    // Poboljšana konverzija datuma
                    val date = getDateFromDocument(doc)
                    
                    Expense(
                        id = id,
                        userId = currentUserId ?: "",
                        amount = amount,
                        date = date,
                        accountId = accountId,
                        description = description,
                        category = category
                    )
                } catch (e: Exception) {
                    Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                    null
                }
            } ?: emptyList()
            
            // Logujemo samo ukupan broj i statistike
            if (expenses.isNotEmpty()) {
                val totalAmount = expenses.sumOf { it.amount }
                val avgAmount = totalAmount / expenses.size.toDouble()
                
                LogUtils.d("ExpenseRepository", 
                    "Osveženo ${expenses.size} rashoda. Ukupno: $totalAmount, Prosek: $avgAmount", 
                    category = "expense")
            } else {
                LogUtils.d("ExpenseRepository", "Nema rashoda za osvežavanje", category = "expense")
            }
            
            _expenses.value = expenses
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Greška pri osvežavanju rashoda", e, category = "expense")
        }
    }
    
    // Додајемо методу за добијање трошкова за одређени период
    fun getExpensesForPeriod(startDate: Date, endDate: Date): Flow<List<Expense>> = callbackFlow {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        Log.d("ExpenseRepository", "Учитавам трошкове за период од $startDateStr до $endDateStr")
        
        if (currentUserId == null) {
            LogUtils.d("ExpenseRepository", "Корисник није пријављен, враћам празну листу", category = "expense")
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val collection = userExpensesCollection
        if (collection == null) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val listener = collection
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
                        
                        LogUtils.e("ExpenseRepository", "Potrebno je kreirati indeks u Firebase konzoli. " +
                               "Koristite sledeći link: $indexUrl", category = "expense")
                        
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
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Expense(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        Log.e("ExpenseRepository", "Грешка при конверзији документа у Expense", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("ExpenseRepository", "Учитано ${expenses.size} расхода за период")
                trySend(expenses)
            }
        
        // IMPORTANT: This ensures the listener is removed when the flow is cancelled
        awaitClose { 
            LogUtils.d("ExpenseRepository", "Затварам listener за расходе за период", category = "expense")
            listener.remove() 
        }
    }
    
    // Добијање расхода за одређени рачун
    fun getExpensesForAccount(accountId: String): Flow<List<Expense>> = callbackFlow {
        LogUtils.i("ExpenseRepository", "Učitavam troškove za račun: $accountId", category = "expense")
        
        if (currentUserId == null) {
            LogUtils.d("ExpenseRepository", "Корисник није пријављен, враћам празну листу", category = "expense")
            trySend(emptyList())
            return@callbackFlow
        }
        
        val collection = userExpensesCollection ?: return@callbackFlow
        
        val listener = collection
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
                        
                        LogUtils.e("ExpenseRepository", "Potrebno je kreirati indeks u Firebase konzoli. " +
                               "Koristite sledeći link: $indexUrl", category = "expense")
                        
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
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Expense(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        LogUtils.e("ExpenseRepository", "Greška pri konverziji dokumenta u Expense", e, category = "expense")
                        null
                    }
                } ?: emptyList()
                
                // Log samo ukupan broj i statistike umesto pojedinačnih stavki
                if (expenses.isNotEmpty()) {
                    val totalAmount = expenses.sumOf { it.amount }
                    val avgAmount = if (expenses.isNotEmpty()) totalAmount / expenses.size else 0.0
                    val minAmount = expenses.minOfOrNull { it.amount } ?: 0.0
                    val maxAmount = expenses.maxOfOrNull { it.amount } ?: 0.0
                    
                    LogUtils.i("ExpenseRepository", 
                        "Učitano ${expenses.size} rashoda za račun $accountId. " +
                        "Ukupno: $totalAmount, Prosek: $avgAmount, Min: $minAmount, Max: $maxAmount", 
                        category = "expense")
                    
                    // Samo u VERBOSE modu prikazati distribuciju vrednosti (opciono)
                    if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                        // Grupisati po kategorijama za bolji pregled
                        val categoryDistribution = expenses.groupBy { it.category }
                            .mapValues { entry -> entry.value.sumOf { it.amount } }
                        
                        LogUtils.d("ExpenseRepository", "Distribucija troškova po kategorijama za račun $accountId: $categoryDistribution", 
                            category = "expense")
                        
                        // Grupisati po opsegu vrednosti
                        val valueDistribution = expenses.groupBy { expense ->
                            when {
                                expense.amount < 1000 -> "< 1,000"
                                expense.amount < 5000 -> "1,000 - 5,000"
                                expense.amount < 10000 -> "5,000 - 10,000"
                                else -> "> 10,000"
                            }
                        }.mapValues { it.value.size }
                        
                        LogUtils.d("ExpenseRepository", "Distribucija troškova po iznosima za račun $accountId: $valueDistribution", 
                            category = "expense")
                    }
                } else {
                    LogUtils.i("ExpenseRepository", "Nema učitanih rashoda za račun $accountId.", category = "expense")
                }
                
                trySend(expenses)
            }
        
        awaitClose { 
            LogUtils.d("ExpenseRepository", "Zatvaram listener za rashode za račun", category = "expense")
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
            
            val snapshot = userExpensesCollection?.get()?.await()
            
            val batch = firestore.batch()
            for (document in snapshot?.documents ?: emptyList()) {
                batch.delete(userExpensesCollection?.document(document.id) ?: continue)
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
        
        LogUtils.d("ExpenseRepository", "Učitavam troškove za račun: $accountId i period od $startDateStr do $endDateStr", 
            category = "expense")
        
        if (currentUserId == null) {
            LogUtils.d("ExpenseRepository", "Корисник није пријављен, враћам празну листу", category = "expense")
            return emptyList()
        }
        
        try {
            // Filtriramo rashode za račun u datom vremenskom periodu
            val expenses = _expenses.value.filter { expense -> 
                expense.accountId == accountId &&
                expense.date >= startDateStr &&
                expense.date <= endDateStr
            }
            
            // Logujemo samo ukupan broj i statistike
            if (expenses.isNotEmpty()) {
                val totalAmount = expenses.sumOf { it.amount }
                val avgAmount = if (expenses.isNotEmpty()) totalAmount / expenses.size else 0.0
                
                LogUtils.d("ExpenseRepository", 
                    "Filtrirano ${expenses.size} rashoda za račun $accountId i period. " +
                    "Ukupno: $totalAmount, Prosek: $avgAmount", 
                    category = "expense")
                
                // Samo u VERBOSE modu prikazati distribuciju po datumima
                if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                    // Grupisati po mesecima za bolji pregled
                    val monthDistribution = expenses.groupBy { 
                        val parts = it.date.split("-")
                        if (parts.size >= 2) "${parts[0]}-${parts[1]}" else it.date 
                    }.mapValues { it.value.sumOf { expense -> expense.amount } }
                    
                    LogUtils.d("ExpenseRepository", "Mesečna distribucija troškova za period: $monthDistribution", 
                        category = "expense")
                }
            } else {
                LogUtils.d("ExpenseRepository", "Nema rashoda za račun $accountId u periodu od $startDateStr do $endDateStr", 
                    category = "expense")
            }
            
            return expenses
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Greška pri filtriranju rashoda za račun i period", e, category = "expense")
            return emptyList()
        }
    }
    
    // Добијање расхода по ID-у
    suspend fun getExpenseById(expenseId: String): Expense? {
        return try {
            LogUtils.d("ExpenseRepository", "Учитавам расход по ID-у: $expenseId", category = "expense")
            
            if (currentUserId == null) {
                LogUtils.d("ExpenseRepository", "Корисник није пријављен", category = "expense")
                return null
            }
            
            val doc = userExpensesCollection?.document(expenseId)?.get()?.await()
            
            if (doc == null || !doc.exists()) {
                LogUtils.d("ExpenseRepository", "Расход није пронађен: $expenseId", category = "expense")
                return null
            }
            
            val id = doc.id
            val amount = doc.getDouble("amount") ?: 0.0
            val description = doc.getString("description") ?: ""
            val category = doc.getString("category") ?: ""
            val accountId = doc.getString("accountId") ?: ""
            val date = getDateFromDocument(doc)
            
            Expense(
                id = id,
                userId = currentUserId ?: "",
                amount = amount,
                date = date,
                accountId = accountId,
                description = description,
                category = category
            )
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", "Грешка при учитавању расхода по ID-у: $expenseId", e, category = "expense")
            null
        }
    }
    
    // На kraju klase dodati helper funkciju за конверзију датума
    /**
     * Pomoćna funkcija za dobijanje datuma iz Firestore dokumenta
     */
    private fun getDateFromDocument(doc: DocumentSnapshot): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        try {
            // Prvo dobavljamo vrednost kao Object da bismo proverili tip
            val dateField = doc.get("date")
            
            when (dateField) {
                is com.google.firebase.Timestamp -> {
                    return dateFormat.format(dateField.toDate())
                }
                is String -> {
                    // Ako je string u očekivanom formatu, vratimo ga direktno
                    if (dateField.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        return dateField
                    }
                    // Pokušaj parsiranje ako je u nekom drugom string formatu
                    return try {
                        val parsedDate = dateFormat.parse(dateField)
                        dateFormat.format(parsedDate ?: Date())
                    } catch (e: Exception) {
                        LogUtils.w("ExpenseRepository", 
                            "Neispravan format string datuma za dokument ID: ${doc.id}, koristim današnji datum", 
                            category = "expense")
                        dateFormat.format(Date())
                    }
                }
                is java.util.Date -> {
                    return dateFormat.format(dateField)
                }
                null -> {
                    LogUtils.w("ExpenseRepository", 
                        "Datum je null za dokument ID: ${doc.id}, koristim današnji datum", 
                        category = "expense")
                    return dateFormat.format(Date())
                }
                else -> {
                    LogUtils.w("ExpenseRepository", 
                        "Nepoznat tip datuma (${dateField.javaClass.name}) za dokument ID: ${doc.id}, koristim današnji datum", 
                        category = "expense")
                    return dateFormat.format(Date())
                }
            }
        } catch (e: Exception) {
            LogUtils.e("ExpenseRepository", 
                "Greška pri konverziji datuma iz dokumenta ID: ${doc.id}", e, 
                category = "expense")
            return dateFormat.format(Date())
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