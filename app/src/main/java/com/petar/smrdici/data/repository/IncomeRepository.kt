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
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Добијање колекције прихода за тренутног корисника
    private val userIncomesCollection
        get() = firestore.collection("users").document(currentUserId).collection("incomes")
    
    // Додавање новог прихода
    suspend fun addIncome(income: Income, updateAccountBalance: Boolean = true): Result<Income> {
        return try {
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
            
            // Чувамо приход у бази података
            userIncomesCollection.document(incomeId).set(incomeToAdd).await()
            
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
            Log.e("IncomeRepository", "Грешка при додавању прихода", e)
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
            val incomeDoc = userIncomesCollection.document(incomeId).get().await()
            val income = incomeDoc.toObject(Income::class.java)
            
            // Бришемо приход из базе података
            userIncomesCollection.document(incomeId).delete().await()
            
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
        LogUtils.i("IncomeRepository", "Učitavam sve prihode", category = "income")
        
        val listener = userIncomesCollection
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
                        
                        val income = Income(id, amount, description, category, date, accountId)
                        
                        // Više ne logujemo svaki pojedinačni prihod
                        income
                    } catch (e: Exception) {
                        Log.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e)
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
                        "Učitano ${incomes.size} prihoda. " +
                        "Ukupno: $totalAmount, Prosek: $avgAmount, Min: $minAmount, Max: $maxAmount", 
                        category = "income")
                    
                    // Samo u VERBOSE modu prikazati distribuciju vrednosti (opciono)
                    if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                        // Grupisati po opsegu vrednosti za bolji pregled
                        val distribution = incomes.groupBy { income ->
                            when {
                                income.amount < 1000 -> "< 1,000"
                                income.amount < 5000 -> "1,000 - 5,000"
                                income.amount < 10000 -> "5,000 - 10,000"
                                else -> "> 10,000"
                            }
                        }.mapValues { it.value.size }
                        
                        LogUtils.d("IncomeRepository", "Distribucija prihoda po iznosima: $distribution", 
                            category = "income")
                    }
                } else {
                    LogUtils.i("IncomeRepository", "Nema učitanih prihoda.", category = "income")
                }
                
                // Ažuriramo lokalni keš
                _incomes.value = incomes
                
                // Šaljemo novu listu
                trySend(incomes)
            }
        
        awaitClose { 
            Log.d("IncomeRepository", "Zatvaram listener za prihode")
            listener.remove() 
        }
    }
    
    // Освежавање листе прихода (користи се интерно)
    private suspend fun refreshIncomes() {
        try {
            LogUtils.d("IncomeRepository", "Osvežavam listu prihoda", category = "income")
            
            val snapshot = userIncomesCollection
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val incomes = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val accountId = doc.getString("accountId") ?: ""
                    
                    // Poboljšana konverzija datuma
                    val date = getDateFromDocument(doc)
                    
                    val income = Income(id, amount, description, category, date, accountId)
                    
                    // Više ne logujemo svaki pojedinačni prihod
                    income
                } catch (e: Exception) {
                    Log.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e)
                    null
                }
            }
            
            // Logujemo samo ukupan broj i statistike
            if (incomes.isNotEmpty()) {
                val totalAmount = incomes.sumOf { it.amount }
                val avgAmount = if (incomes.isNotEmpty()) totalAmount / incomes.size else 0.0
                
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
        
        LogUtils.i("IncomeRepository", "Učitavam prihode za period od $startDateStr do $endDateStr", category = "income")
        
        val listener = userIncomesCollection
            .whereGreaterThanOrEqualTo("date", startDateStr)
            .whereLessThanOrEqualTo("date", endDateStr)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("IncomeRepository", "Greška pri slušanju prihoda za period", error, category = "income")
                    
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
                        
                        val income = Income(id, amount, description, category, date, accountId)
                        
                        // Više ne logujemo svaki pojedinačni prihod
                        income
                    } catch (e: Exception) {
                        LogUtils.e("IncomeRepository", "Greška pri konverziji dokumenta u Income", e, category = "income")
                        null
                    }
                } ?: emptyList()
                
                // Log samo ukupan broj i statistike
                if (incomes.isNotEmpty()) {
                    val totalAmount = incomes.sumOf { it.amount }
                    val avgAmount = if (incomes.isNotEmpty()) totalAmount / incomes.size else 0.0
                    val minAmount = incomes.minOfOrNull { it.amount } ?: 0.0
                    val maxAmount = incomes.maxOfOrNull { it.amount } ?: 0.0
                    
                    LogUtils.i("IncomeRepository", 
                        "Učitano ${incomes.size} prihoda za period $startDateStr - $endDateStr. " +
                        "Ukupno: $totalAmount, Prosek: $avgAmount, Min: $minAmount, Max: $maxAmount", 
                        category = "income")
                    
                    // Samo u VERBOSE modu prikazati dodatne detalje
                    if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                        // Grupisati po mesecima
                        val monthDistribution = incomes.groupBy { 
                            val parts = it.date.split("-") 
                            if (parts.size >= 2) "${parts[0]}-${parts[1]}" else it.date
                        }.mapValues { it.value.sumOf { income -> income.amount } }
                        
                        LogUtils.d("IncomeRepository", "Mesečna distribucija prihoda za period: $monthDistribution", 
                            category = "income")
                    }
                } else {
                    LogUtils.i("IncomeRepository", "Nema prihoda za period $startDateStr - $endDateStr", 
                        category = "income")
                }
                
                trySend(incomes)
            }
        
        awaitClose { 
            LogUtils.d("IncomeRepository", "Zatvaram listener za prihode za period", category = "income")
            listener.remove() 
        }
    }
    
    // Добијање прихода за одређени рачун
    fun getIncomesForAccount(accountId: String): Flow<List<Income>> = callbackFlow {
        LogUtils.i("IncomeRepository", "Učitavam prihode za račun: $accountId", category = "income")
        
        val listener = userIncomesCollection
            .whereEqualTo("accountId", accountId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("IncomeRepository", "Greška pri slušanju prihoda za račun", error, category = "income")
                    
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
                        
                        val income = Income(id, amount, description, category, date, accountId)
                        
                        // Više ne logujemo svaki pojedinačni prihod
                        income
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
            LogUtils.d("IncomeRepository", "Zatvaram listener za prihode za račun", category = "income")
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
    fun getIncomes(): Flow<List<Income>> {
        return _incomes.asStateFlow()
    }
    
    // Брисање свих прихода (за операцију увоза)
    suspend fun deleteAllIncomes() {
        try {
            Log.d("IncomeRepository", "Бришем све приходе")
            
            val snapshot = userIncomesCollection.get().await()
            
            val batch = firestore.batch()
            for (document in snapshot.documents) {
                batch.delete(userIncomesCollection.document(document.id))
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
     * koja podržava različite formate datuma (String, Timestamp, Date)
     */
    private fun getDateFromDocument(doc: DocumentSnapshot): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        try {
            // IZMENA: Prvo pokušaj dobiti kao Timestamp (preferirani format)
            val timestamp = doc.getTimestamp("date")
            if (timestamp != null) {
                return dateFormat.format(timestamp.toDate())
            }
            
            // Zatim, bezbedno pokušaj dobiti kao Date
            val date = doc.getDate("date")
            if (date != null) {
                return dateFormat.format(date)
            }
            
            // Na kraju pokušaj dobiti kao String
            try {
                val dateStr = doc.getString("date")
                if (dateStr != null && dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    return dateStr
                }
            } catch (e: Exception) {
                // Ignorišemo grešku za getString jer znamo da polje možda nije String
                LogUtils.d("IncomeRepository", "Datum nije String: ${e.message}", category = "income")
            }
            
            // Ako nije nijedan od podržanih tipova, vrati današnji datum
            LogUtils.w("IncomeRepository", "Datum nije u prepoznatom formatu za dokument ID: ${doc.id}, koristim današnji datum", 
                category = "income")
            return dateFormat.format(Date())
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Greška pri konverziji datuma iz dokumenta ID: ${doc.id}", e, 
                category = "income")
            // U slučaju greške, vrati današnji datum
            return dateFormat.format(Date())
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