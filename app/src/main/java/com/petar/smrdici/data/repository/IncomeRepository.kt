package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.petar.smrdici.data.model.Income
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import com.petar.smrdici.utils.LogUtils

class IncomeRepository private constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _incomes = MutableStateFlow<List<Income>>(emptyList())
    
    // Референца на AccountRepository
    private var accountRepository: AccountRepository? = null
    
    fun setAccountRepository(accountRepo: AccountRepository) {
        this.accountRepository = accountRepo
    }
    
    // Добијање тренутног корисника
    private val currentUserId: String?
        get() = auth.currentUser?.uid
    
    // Добијање колекције прихода за тренутног корисника
    private val userIncomesCollection
        get() = currentUserId?.let { uid ->
            firestore.collection("users").document(uid).collection("incomes")
        }
    
    // Додавање новог прихода
    suspend fun addIncome(income: Income, updateAccountBalance: Boolean = true): Result<Income> {
        return try {
            if (currentUserId == null) {
                LogUtils.d("IncomeRepository", "Корисник није пријављен, не могу додати приход", category = "income")
                return Result.failure(IllegalStateException("Корисник није пријављен"))
            }
            
            val collection = userIncomesCollection ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            
            Log.d("IncomeRepository", "Додајем приход: $income")
            
            // Генеришемо ID ако није већ постављен
            val incomeId = income.id.ifEmpty { UUID.randomUUID().toString() }
            
            // Осигурамо да имамо валидан датум у формату "YYYY-MM-DD"
            val validDate = if (income.date.isEmpty() || !income.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(Date())
            } else {
                income.date
            }
            
            val incomeToAdd = income.copy(id = incomeId, date = validDate)
            
            // Pretvaramo validDate string u Date objekat, pa u Timestamp za Firebase
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val dateObject = dateFormat.parse(validDate) ?: Date()
            
            // Kreiramo mapu podataka koja će biti sačuvana u Firestore
            val incomeMap = mapOf(
                "id" to incomeId,
                "userId" to income.userId,
                "amount" to income.amount,
                "description" to income.description,
                "category" to income.category,
                "date" to com.google.firebase.Timestamp(dateObject),
                "accountId" to income.accountId,
                "createdAt" to income.createdAt,
                "updatedAt" to income.updatedAt
            )
            
            // Чувамо приход у бази података
            collection.document(incomeId).set(incomeMap).await()
            
            // Ажурирамо баланс рачуна (повећавамо га) само ако је затражено
            if (updateAccountBalance) {
                Log.d("IncomeRepository", "Ажурирам баланс рачуна: ${income.accountId} за износ: ${income.amount}")
                accountRepository?.updateAccountBalance(income.accountId, income.amount)
            } else {
                Log.d("IncomeRepository", "Прескачем ажурирање баланса рачуна за приход: $incomeId")
            }
            
            // Ажурирамо локални кеш
            refreshIncomes()
            
            Result.success(incomeToAdd)
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Грешка при додавању прихода", e, category = "income")
            Result.failure(e)
        }
    }
    
    // Ажурирање постојећег прихода
    suspend fun updateIncome(income: Income): Result<Income> {
        return try {
            LogUtils.i("IncomeRepository", "Ažuriram prihod: $income", category = "income")
            
            // Проверавамо да ли је ID валидан
            if (income.id.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID прихода"))
            }
            
            // Прво налазимо стари приход да бисмо добили стари износ
            val oldIncomeDoc = userIncomesCollection?.document(income.id)?.get()?.await()
            val oldIncome = oldIncomeDoc?.toObject(Income::class.java)
            
            // Осигурамо да имамо валидан датум у формату "YYYY-MM-DD"
            val validDate = if (income.date.isEmpty() || !income.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(Date())
            } else {
                income.date
            }
            
            val incomeToUpdate = income.copy(date = validDate)
            
            // Pretvaramo validDate string u Date objekat, pa u Timestamp za Firebase
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val dateObject = dateFormat.parse(validDate) ?: Date()
            
            // Kreiramo mapu podataka koja će biti sačuvana u Firestore
            val incomeMap = mapOf(
                "id" to incomeToUpdate.id,
                "amount" to incomeToUpdate.amount,
                "description" to incomeToUpdate.description,
                "category" to incomeToUpdate.category,
                "date" to com.google.firebase.Timestamp(dateObject),
                "accountId" to incomeToUpdate.accountId
            )
            
            // Ажурирамо приход у бази података
            userIncomesCollection?.document(incomeToUpdate.id)?.set(incomeMap)?.await()
            
            // Ажурирамо баланс рачуна
            if (oldIncome != null) {
                // Одузимамо стари износ (смањујемо баланс)
                accountRepository?.updateAccountBalance(oldIncome.accountId, -oldIncome.amount)
                // Додајемо нови износ (повећавамо баланс)
                accountRepository?.updateAccountBalance(incomeToUpdate.accountId, incomeToUpdate.amount)
            }
            
            // Ажурирамо локални кеш
            refreshIncomes()
            
            Result.success(incomeToUpdate)
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Greška pri ažuriranju prihoda", e, category = "income")
            Result.failure(e)
        }
    }
    
    // Брисање прихода
    suspend fun deleteIncome(incomeId: String): Result<Unit> {
        return try {
            Log.d("IncomeRepository", "Бришем приход: $incomeId")
            
            // Проверавамо да ли је ID валидан
            if (incomeId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID прихода"))
            }
            
            // Прво налазимо приход да бисмо добили износ и ID рачуна
            val incomeDoc = userIncomesCollection?.document(incomeId)?.get()?.await()
            val income = incomeDoc?.toObject(Income::class.java)
            
            // Бришемо приход из базе података
            userIncomesCollection?.document(incomeId)?.delete()?.await()
            
            // Ако смо успешно добавили приход, враћамо баланс рачуна (смањујемо га)
            if (income != null) {
                accountRepository?.updateAccountBalance(income.accountId, -income.amount)
            }
            
            // Ажурирамо локални кеш
            refreshIncomes()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IncomeRepository", "Грешка при брисању прихода", e)
            Result.failure(e)
        }
    }
    
    // Добијање свих прихода за тренутног корисника
    fun getAllIncomes(): Flow<List<Income>> = callbackFlow {
        LogUtils.i("IncomeRepository", "Учитавам све приходе", category = "income")
        
        if (currentUserId == null) {
            LogUtils.d("IncomeRepository", "Корисник није пријављен, враћам празну листу", category = "income")
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val collection = userIncomesCollection
        if (collection == null) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val listener = collection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("IncomeRepository", "Greška pri slušanju prihoda", error)
                    close(error)
                    return@addSnapshotListener
                }
                
                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Income(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        Log.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e)
                        null
                    }
                } ?: emptyList()
                
                // Log samo ukupan broj i statistike umesto pojedinačnih stavki
                if (incomes.isNotEmpty()) {
                    val totalAmount = incomes.sumOf { it.amount }
                    val avgAmount = totalAmount / incomes.size.toDouble()
                    val minAmount = incomes.minOfOrNull { it.amount } ?: 0.0
                    val maxAmount = incomes.maxOfOrNull { it.amount } ?: 0.0
                    
                    LogUtils.i("IncomeRepository", 
                        "Učitano ${incomes.size} prihoda. " +
                        "Ukupno: $totalAmount, Prosek: $avgAmount, Min: $minAmount, Max: $maxAmount", 
                        category = "income")
                }
                
                // Ažuriramo lokalni keš
                _incomes.value = incomes
                
                // Šaljemo novu listu
                trySend(incomes)
            }
        
        // IMPORTANT: This ensures the listener is removed when the flow is cancelled
        awaitClose {
            LogUtils.d("IncomeRepository", "Затварам listener за приходе", category = "income")
            listener.remove()
        }
    }
    
    // Освежавање листе прихода (користи се интерно)
    private suspend fun refreshIncomes() {
        try {
            LogUtils.d("IncomeRepository", "Osvežavam listu prihoda", category = "income")
            
            val snapshot = userIncomesCollection?.get()?.await()
            
            val incomes = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val accountId = doc.getString("accountId") ?: ""
                    
                    // Poboljšana konverzija datuma
                    val date = getDateFromDocument(doc)
                    
                    Income(
                        id = id,
                        userId = currentUserId ?: "",
                        amount = amount,
                        date = date,
                        accountId = accountId,
                        description = description,
                        category = category
                    )
                } catch (e: Exception) {
                    Log.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e)
                    null
                }
            } ?: emptyList()
            
            // Logujemo samo ukupan broj i statistike
            if (incomes.isNotEmpty()) {
                val totalAmount = incomes.sumOf { it.amount }
                val avgAmount = totalAmount / incomes.size.toDouble()
                
                LogUtils.d("IncomeRepository", 
                    "Osveženo ${incomes.size} prihoda. Ukupno: $totalAmount, Prosek: $avgAmount", 
                    category = "income")
            } else {
                LogUtils.d("IncomeRepository", "Nema prihoda za osvežavanje", category = "income")
            }
            
            _incomes.value = incomes
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Greška pri osvežavanju prihoda", e, category = "income")
        }
    }
    
    // Добијање прихода за одређени период
    fun getIncomesForPeriod(startDate: Date, endDate: Date): Flow<List<Income>> = callbackFlow {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        Log.d("IncomeRepository", "Учитавам приходе за период од $startDateStr до $endDateStr")
        
        if (currentUserId == null) {
            LogUtils.d("IncomeRepository", "Корисник није пријављен, враћам празну листу", category = "income")
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val collection = userIncomesCollection
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
                    LogUtils.e("IncomeRepository", "Greška pri slušanju prihoda за период", error, category = "income")
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                            val matchResult = urlPattern.find(msg)
                            matchResult?.value
                        }
                        
                        LogUtils.e("IncomeRepository", "Potrebno je kreirati indeks u Firebase konzoli. " +
                               "Koristite sledeći link: $indexUrl", category = "income")
                        
                        // Шаљемо празну листу уместо да затворимо flow са грешком
                        trySend(emptyList())
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                
                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Income(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        LogUtils.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e, category = "income")
                        null
                    }
                } ?: emptyList()
                
                Log.d("IncomeRepository", "Учитано ${incomes.size} прихода за период")
                trySend(incomes)
            }
        
        // IMPORTANT: This ensures the listener is removed when the flow is cancelled
        awaitClose { 
            LogUtils.d("IncomeRepository", "Затварам listener за приходе за период", category = "income")
            listener.remove() 
        }
    }
    
    // Добијање прихода за одређени рачун
    fun getIncomesForAccount(accountId: String): Flow<List<Income>> = callbackFlow {
        LogUtils.i("IncomeRepository", "Učitavam prihode za račun: $accountId", category = "income")
        
        if (currentUserId == null) {
            LogUtils.d("IncomeRepository", "Корисник није пријављен, враћам празну листу", category = "income")
            trySend(emptyList())
            return@callbackFlow
        }
        
        val collection = userIncomesCollection ?: return@callbackFlow
        
        val listener = collection
            .whereEqualTo("accountId", accountId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("IncomeRepository", "Greška pri slušanju prihoda за račun", error, category = "income")
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                            val matchResult = urlPattern.find(msg)
                            matchResult?.value
                        }
                        
                        LogUtils.e("IncomeRepository", "Potrebno je kreirati indeks u Firebase konzoli. " +
                               "Koristite sledeći link: $indexUrl", category = "income")
                        
                        // Шаљемо празну листу уместо да затворимо flow са грешком
                        trySend(emptyList())
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                
                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Income(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        LogUtils.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e, category = "income")
                        null
                    }
                } ?: emptyList()
                
                // Log samo ukupan broj i statistike umesto pojedinačnih stavki
                if (incomes.isNotEmpty()) {
                    val totalAmount = incomes.sumOf { it.amount }
                    val avgAmount = if (incomes.isNotEmpty()) totalAmount / incomes.size else 0.0
                    val minAmount = incomes.minOfOrNull { it.amount } ?: 0.0
                    val maxAmount = incomes.maxOfOrNull { it.amount } ?: 0.0
                    
                    LogUtils.i("IncomeRepository", 
                        "Učitano ${incomes.size} prihoda za račun $accountId. " +
                        "Ukupno: $totalAmount, Prosek: $avgAmount, Min: $minAmount, Max: $maxAmount", 
                        category = "income")
                    
                    // Samo u VERBOSE modu prikazati distribuciju vrednosti (opciono)
                    if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                        // Grupisati po kategorijama za bolji pregled
                        val categoryDistribution = incomes.groupBy { it.category }
                            .mapValues { entry -> entry.value.sumOf { it.amount } }
                        
                        LogUtils.d("IncomeRepository", "Distribucija prihoda po kategorijama za račun $accountId: $categoryDistribution", 
                            category = "income")
                        
                        // Grupisati po opsegu vrednosti
                        val valueDistribution = incomes.groupBy { income ->
                            when {
                                income.amount < 1000 -> "< 1,000"
                                income.amount < 5000 -> "1,000 - 5,000"
                                income.amount < 10000 -> "5,000 - 10,000"
                                else -> "> 10,000"
                            }
                        }.mapValues { it.value.size }
                        
                        LogUtils.d("IncomeRepository", "Distribucija prihoda po iznosima za račun $accountId: $valueDistribution", 
                            category = "income")
                    }
                } else {
                    LogUtils.i("IncomeRepository", "Nema učitanih prihoda za račun $accountId.", category = "income")
                }
                
                trySend(incomes)
            }
        
        awaitClose { 
            Log.d("IncomeRepository", "Затварам listener за приходе за račun")
            listener.remove() 
        }
    }
    
    // Добијање прихода за одређени рачун у одређеном периоду
    fun getIncomesForAccount(accountId: String, startDate: Date, endDate: Date): List<Income> {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        LogUtils.d("IncomeRepository", "Učitavam prihode za račun: $accountId i period od $startDateStr do $endDateStr", 
            category = "income")
        
        if (currentUserId == null) {
            LogUtils.d("IncomeRepository", "Корисник није пријављен, враћам празну листу", category = "income")
            return emptyList()
        }
        
        try {
            // Filtriramo prihode za račun u datom vremenskom periodu
            val incomes = _incomes.value.filter { income -> 
                income.accountId == accountId &&
                income.date >= startDateStr &&
                income.date <= endDateStr
            }
            
            // Logujemo samo ukupan broj i statistike
            if (incomes.isNotEmpty()) {
                val totalAmount = incomes.sumOf { it.amount }
                val avgAmount = if (incomes.isNotEmpty()) totalAmount / incomes.size else 0.0
                
                LogUtils.d("IncomeRepository", 
                    "Filtrirano ${incomes.size} prihoda za račun $accountId i period. " +
                    "Ukupno: $totalAmount, Prosek: $avgAmount", 
                    category = "income")
                
                // Samo u VERBOSE modu prikazati distribuciju po datumima
                if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                    // Grupisati po mesecima za bolji pregled
                    val monthDistribution = incomes.groupBy { 
                        val parts = it.date.split("-")
                        if (parts.size >= 2) "${parts[0]}-${parts[1]}" else it.date 
                    }.mapValues { it.value.sumOf { income -> income.amount } }
                    
                    LogUtils.d("IncomeRepository", "Mesečna distribucija prihoda za period: $monthDistribution", 
                        category = "income")
                }
            } else {
                LogUtils.d("IncomeRepository", "Nema prihoda za račun $accountId u periodu od $startDateStr do $endDateStr", 
                    category = "income")
            }
            
            return incomes
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Greška pri filtriranju prihoda za račun i period", e, category = "income")
            return emptyList()
        }
    }
    
    // Добијање свих прихода као Flow за observovanje
    fun getIncomes(): Flow<List<Income>> = callbackFlow {
        LogUtils.i("IncomeRepository", "Учитавам све приходе", category = "income")
        
        if (currentUserId == null) {
            LogUtils.d("IncomeRepository", "Корисник није пријављен, враћам празну листу", category = "income")
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val collection = userIncomesCollection
        if (collection == null) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        
        val listener = collection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("IncomeRepository", "Грешка при слушању прихода", error)
                    return@addSnapshotListener
                }
                
                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val amount = doc.getDouble("amount") ?: 0.0
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val accountId = doc.getString("accountId") ?: ""
                        
                        // Poboljšana konverzija datuma
                        val date = getDateFromDocument(doc)
                        
                        Income(
                            id = id,
                            userId = currentUserId ?: "",
                            amount = amount,
                            date = date,
                            accountId = accountId,
                            description = description,
                            category = category
                        )
                    } catch (e: Exception) {
                        Log.e("IncomeRepository", "Грешка при конверзији документа у Income", e)
                        null
                    }
                } ?: emptyList()
                
                // Update the cache
                _incomes.value = incomes
                
                // Emit the new list
                trySend(incomes)
            }
        
        // Remove the listener when the flow is cancelled
        awaitClose { 
            LogUtils.d("IncomeRepository", "Затварам listener за приходе", category = "income")
            listener.remove() 
        }
    }
    
    // Брисање свих прихода (за операцију увоза)
    suspend fun deleteAllIncomes() {
        try {
            Log.d("IncomeRepository", "Бришем све приходе")
            
            val snapshot = userIncomesCollection?.get()?.await()
            
            val batch = firestore.batch()
            for (document in snapshot?.documents ?: emptyList()) {
                batch.delete(userIncomesCollection?.document(document.id) ?: continue)
            }
            
            batch.commit().await()
            
            // Освежавање локалног кеша
            _incomes.value = emptyList()
            
            Log.d("IncomeRepository", "Сви приходи су обрисани")
        } catch (e: Exception) {
            Log.e("IncomeRepository", "Грешка при брисању свих прихода", e)
        }
    }
    
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
                        LogUtils.w("IncomeRepository", 
                            "Neispravan format string datuma za dokument ID: ${doc.id}, koristim današnji datum", 
                            category = "income")
                        dateFormat.format(Date())
                    }
                }
                is java.util.Date -> {
                    return dateFormat.format(dateField)
                }
                null -> {
                    LogUtils.w("IncomeRepository", 
                        "Datum je null za dokument ID: ${doc.id}, koristim današnji datum", 
                        category = "income")
                    return dateFormat.format(Date())
                }
                else -> {
                    LogUtils.w("IncomeRepository", 
                        "Nepoznat tip datuma (${dateField.javaClass.name}) za dokument ID: ${doc.id}, koristim današnji datum", 
                        category = "income")
                    return dateFormat.format(Date())
                }
            }
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", 
                "Greška pri konverziji datuma iz dokumenta ID: ${doc.id}", e, 
                category = "income")
            return dateFormat.format(Date())
        }
    }
    
    // Добијање прихода по ID-у
    suspend fun getIncomeById(incomeId: String): Income? {
        return try {
            LogUtils.d("IncomeRepository", "Учитавам приход по ID-у: $incomeId", category = "income")
            
            if (currentUserId == null) {
                LogUtils.d("IncomeRepository", "Корисник није пријављен", category = "income")
                return null
            }
            
            val doc = userIncomesCollection?.document(incomeId)?.get()?.await()
            
            if (doc == null || !doc.exists()) {
                LogUtils.d("IncomeRepository", "Приход није пронађен: $incomeId", category = "income")
                return null
            }
            
            val id = doc.id
            val amount = doc.getDouble("amount") ?: 0.0
            val description = doc.getString("description") ?: ""
            val category = doc.getString("category") ?: ""
            val accountId = doc.getString("accountId") ?: ""
            val date = getDateFromDocument(doc)
            
            Income(
                id = id,
                userId = currentUserId ?: "",
                amount = amount,
                date = date,
                accountId = accountId,
                description = description,
                category = category
            )
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Грешка при учитавању прихода по ID-у: $incomeId", e, category = "income")
            null
        }
    }
    
    companion object {
        @Volatile
        private var instance: IncomeRepository? = null
        
        fun getInstance(): IncomeRepository {
            return instance ?: synchronized(this) {
                val newInstance = IncomeRepository()
                instance = newInstance
                newInstance
            }
        }
        
        fun initialize() {
            if (instance == null) {
                instance = IncomeRepository()
                // Повезујемо са AccountRepository
                instance?.setAccountRepository(AccountRepository.getInstance())
            }
        }
    }
} 