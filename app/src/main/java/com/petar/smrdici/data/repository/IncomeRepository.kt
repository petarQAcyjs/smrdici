package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Income
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

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
        Log.d("IncomeRepository", "Учитавам све приходе - BUDGET FIX")
        
        val listener = userIncomesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("IncomeRepository", "Грешка при слушању прихода", error)
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
                        
                        // Учитавање датума у формату "YYYY-MM-DD"
                        val dateStr = doc.getString("date") ?: ""
                        val date = if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            dateStr
                        } else {
                            // Ако није у очекиваном формату, форматирај данашњи датум
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(Date())
                        }
                        
                        val income = Income(id, amount, description, category, date, accountId)
                        
                        Log.d("IncomeRepository", "Учитан приход ID: ${income.id}, износ: ${income.amount}, датум: ${income.getFormattedDate()}")
                        
                        income
                    } catch (e: Exception) {
                        Log.e("IncomeRepository", "Грешка при конверзији документа у Income", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("IncomeRepository", "Учитано ${incomes.size} прихода - BUDGET FIX")
                
                // Детаљнији лог за дебагирање - приказује све учитане приходе
                if (incomes.isNotEmpty()) {
                    Log.d("IncomeRepository", "Учитани приходи: ${incomes.map { "${it.id} (${it.amount})" }}")
                } else {
                    Log.d("IncomeRepository", "Нема учитаних прихода. Проверите Firebase конекцију и податке.")
                }
                
                // Ажурирамо локални кеш
                _incomes.value = incomes
                
                // Шаљемо нову листу
                trySend(incomes)
            }
        
        awaitClose { 
            Log.d("IncomeRepository", "Затварам listener за приходе")
            listener.remove() 
        }
    }
    
    // Освежавање листе прихода (користи се интерно)
    private suspend fun refreshIncomes() {
        try {
            Log.d("IncomeRepository", "Освежавам листу прихода")
            
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
                    
                    // Учитавање датума
                    val dateStr = doc.getString("date") ?: ""
                    val date = if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        dateStr
                    } else {
                        // Ако није у очекиваном формату, форматирај данашњи датум
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        dateFormat.format(Date())
                    }
                    
                    val income = Income(id, amount, description, category, date, accountId)
                    
                    Log.d("IncomeRepository", "Учитан приход ID: ${income.id}, износ: ${income.amount}, датум: ${income.getFormattedDate()}")
                    
                    income
                } catch (e: Exception) {
                    Log.e("IncomeRepository", "Грешка при конверзији документа у Income", e)
                    null
                }
            }
            
            Log.d("IncomeRepository", "Освежено ${incomes.size} прихода")
            
            _incomes.value = incomes
        } catch (e: Exception) {
            Log.e("IncomeRepository", "Грешка при освежавању прихода", e)
        }
    }
    
    // Добијање прихода за одређени период
    fun getIncomesForPeriod(startDate: Date, endDate: Date): Flow<List<Income>> = callbackFlow {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        Log.d("IncomeRepository", "Учитавам приходе за период од $startDateStr до $endDateStr")
        
        val listener = userIncomesCollection
            .whereGreaterThanOrEqualTo("date", startDateStr)
            .whereLessThanOrEqualTo("date", endDateStr)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("IncomeRepository", "Грешка при слушању прихода за период", error)
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                            val matchResult = urlPattern.find(msg)
                            matchResult?.value
                        }
                        
                        Log.e("IncomeRepository", "Потребно је креирати индекс у Firebase конзоли. " +
                               "Користите следећи линк: $indexUrl")
                        
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
                        
                        // Учитавање датума
                        val dateStr = doc.getString("date") ?: ""
                        val date = if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            dateStr
                        } else {
                            // Ако није у очекиваном формату, форматирај данашњи датум
                            dateFormat.format(Date())
                        }
                        
                        val income = Income(id, amount, description, category, date, accountId)
                        
                        Log.d("IncomeRepository", "Учитан приход за период ID: ${income.id}, износ: ${income.amount}, датум: ${income.getFormattedDate()}")
                        
                        income
                    } catch (e: Exception) {
                        Log.e("IncomeRepository", "Грешка при конверзији документа у Income", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("IncomeRepository", "Учитано ${incomes.size} прихода за период")
                trySend(incomes)
            }
        
        awaitClose { 
            Log.d("IncomeRepository", "Затварам listener за приходе за период")
            listener.remove() 
        }
    }
    
    // Добијање прихода за одређени рачун
    fun getIncomesForAccount(accountId: String): Flow<List<Income>> = callbackFlow {
        Log.d("IncomeRepository", "Учитавам приходе за рачун: $accountId - BUDGET FIX")
        
        val listener = userIncomesCollection
            .whereEqualTo("accountId", accountId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("IncomeRepository", "Грешка при слушању прихода за рачун", error)
                    
                    // Проверавамо да ли је грешка везана за недостајући индекс
                    if (error.message?.contains("FAILED_PRECONDITION") == true && 
                        error.message?.contains("The query requires an index") == true) {
                        
                        // Извлачимо URL за креирање индекса из поруке о грешци
                        val indexUrl = error.message?.let { msg ->
                            val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                            val matchResult = urlPattern.find(msg)
                            matchResult?.value
                        }
                        
                        Log.e("IncomeRepository", "Потребно је креирати индекс у Firebase конзоли. " +
                               "Користите следећи линк: $indexUrl")
                        
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
                        
                        // Учитавање датума
                        val dateStr = doc.getString("date") ?: ""
                        val date = if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            dateStr
                        } else {
                            // Ако није у очекиваном формату, форматирај данашњи датум
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            dateFormat.format(Date())
                        }
                        
                        val income = Income(id, amount, description, category, date, accountId)
                        
                        Log.d("IncomeRepository", "Учитан приход за рачун ID: ${income.id}, износ: ${income.amount}, датум: ${income.getFormattedDate()}")
                        
                        income
                    } catch (e: Exception) {
                        Log.e("IncomeRepository", "Грешка при конверзији документа у Income", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d("IncomeRepository", "Учитано ${incomes.size} прихода за рачун - BUDGET FIX")
                
                // Детаљнији лог за дебагирање
                if (incomes.isNotEmpty()) {
                    Log.d("IncomeRepository", "Учитани приходи за рачун: ${incomes.map { "${it.id} (${it.amount})" }}")
                } else {
                    Log.d("IncomeRepository", "Нема учитаних прихода за рачун $accountId.")
                }
                
                trySend(incomes)
            }
        
        awaitClose { 
            Log.d("IncomeRepository", "Затварам listener за приходе за рачун")
            listener.remove() 
        }
    }
    
    // Добијање прихода за одређени рачун у одређеном периоду
    fun getIncomesForAccount(accountId: String, startDate: Date, endDate: Date): List<Income> {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)
        
        Log.d("IncomeRepository", "Učitavam prihode za račun: $accountId i period od $startDateStr do $endDateStr")
        
        try {
            // Filtriramo prihode za račun u datom vremenskom periodu
            val incomes = _incomes.value.filter { income -> 
                income.accountId == accountId &&
                income.date >= startDateStr &&
                income.date <= endDateStr
            }
            
            Log.d("IncomeRepository", "Filtrirano ${incomes.size} prihoda za račun i period")
            return incomes
        } catch (e: Exception) {
            Log.e("IncomeRepository", "Greška pri filtriranju prihoda za račun i period", e)
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
    
    companion object {
        @Volatile
        private var instance: IncomeRepository? = null
        
        fun getInstance(): IncomeRepository {
            return instance ?: synchronized(this) {
                instance ?: IncomeRepository().also { instance = it }
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