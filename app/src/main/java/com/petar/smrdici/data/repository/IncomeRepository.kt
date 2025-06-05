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
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
    
    // Shared incomes collection
    private val sharedIncomesCollection
        get() = firestore.collection("shared_incomes")
    
    // Legacy: Добијање колекције прихода за тренутног корисника
    private val userIncomesCollection
        get() = currentUserId?.let { uid ->
            firestore.collection("users").document(uid).collection("incomes")
        }
    
    // Helper function to convert LocalDate to Timestamp
    private fun localDateToTimestamp(date: LocalDate): Timestamp {
        return Timestamp(date.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond, 0)
    }
    
    // Helper function to convert LocalDateTime to Timestamp
    private fun localDateTimeToTimestamp(dateTime: LocalDateTime): Timestamp {
        return Timestamp(dateTime.atZone(ZoneId.systemDefault()).toInstant().epochSecond, 0)
    }
    
    // Додавање новог прихода
    suspend fun addIncome(income: Income, updateAccountBalance: Boolean = true): Result<Income> {
        return try {
            if (currentUserId == null) {
                LogUtils.d("IncomeRepository", "Корисник није пријављен, не могу додати приход", category = "income")
                return Result.failure(IllegalStateException("Корисник није пријављен"))
            }
            
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
                "date" to Timestamp(dateObject),
                "accountId" to income.accountId,
                "createdAt" to income.createdAt,
                "updatedAt" to income.updatedAt
            )
            
            // Чувамо приход у бази података (shared collection)
            sharedIncomesCollection.document(incomeId).set(incomeMap).await()
            
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
            val oldIncomeDoc = sharedIncomesCollection.document(income.id).get().await()
            val oldIncome = oldIncomeDoc.toObject(Income::class.java)
            
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
                "date" to Timestamp(dateObject),
                "accountId" to incomeToUpdate.accountId
            )
            
            // Ажурирамо приход у бази података (shared collection)
            sharedIncomesCollection.document(incomeToUpdate.id).set(incomeMap).await()
            
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
            LogUtils.i("IncomeRepository", "Brišem prihod: $incomeId", category = "income")
            
            // Проверавамо да ли је ID валидан
            if (incomeId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невалидан ID прихода"))
            }
            
            // Прво налазимо приход да бисмо добили износ и ID рачуна
            val incomeDoc = sharedIncomesCollection.document(incomeId).get().await()
            
            // Instead of using toObject which requires a no-arg constructor, manually extract the fields
            val accountId = incomeDoc.getString("accountId") ?: ""
            val amount = incomeDoc.getDouble("amount") ?: 0.0
            
            // Only proceed if we have valid data
            if (accountId.isNotEmpty() && amount > 0) {
                // Бришемо приход из базе података (shared collection)
                sharedIncomesCollection.document(incomeId).delete().await()
                
                // Враћамо баланс рачуна (смањујемо га)
                accountRepository?.updateAccountBalance(accountId, -amount)
                
                // Ажурирамо локални кеш
                refreshIncomes()
                
                Result.success(Unit)
            } else {
                LogUtils.e("IncomeRepository", "Nedostaju podaci o prihodu za brisanje: accountId=$accountId, amount=$amount", category = "income")
                Result.failure(IllegalStateException("Nedostaju podaci o prihodu"))
            }
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Greška pri brisanju prihoda", e, category = "income")
            Result.failure(e)
        }
    }
    
    // Get income by ID
    suspend fun getIncomeById(incomeId: String): Income? {
        return try {
            val incomeDoc = sharedIncomesCollection.document(incomeId).get().await()
            if (!incomeDoc.exists()) {
                return null
            }
            
            val data = incomeDoc.data ?: return null
            
            // Extract date from Timestamp
            val timestamp = data["date"] as? Timestamp
            val dateStr = if (timestamp != null) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(timestamp.toDate())
            } else {
                ""
            }
            
            Income(
                id = incomeDoc.id,
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
            LogUtils.e("IncomeRepository", "Error getting income by ID: $incomeId", e, category = "income")
            null
        }
    }
    
    // Добијање свих прихода
    fun getIncomes(): Flow<List<Income>> = callbackFlow {
        val listenerRegistration = sharedIncomesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("IncomeRepository", "Greška pri dobavljanju prihoda", error, category = "income")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Manually map document to Income object
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
                        
                        Income(
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
                        LogUtils.e("IncomeRepository", "Greška pri mapiranju dokumenta u Income", e, category = "income")
                        null
                    }
                } ?: emptyList()
                
                trySend(incomes)
            }
        
        awaitClose {
            listenerRegistration.remove()
        }
    }
    
    // Добијање прихода за одређени месец
    fun getIncomesForMonth(year: Int, month: Int): Flow<List<Income>> = callbackFlow {
        // Креирамо датуме за почетак и крај месеца
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1)
        
        // Конвертујемо у Timestamp за Firestore
        val startTimestamp = localDateToTimestamp(startDate)
        val endTimestamp = localDateToTimestamp(endDate)
        
        val listenerRegistration = sharedIncomesCollection
            .whereGreaterThanOrEqualTo("date", startTimestamp)
            .whereLessThan("date", endTimestamp)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    LogUtils.e("IncomeRepository", "Greška pri dobavljanju prihoda za mesec", error, category = "income")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        // Manually map document to Income object
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
                        
                        Income(
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
                        LogUtils.e("IncomeRepository", "Greška pri mapiranju dokumenta u Income", e, category = "income")
                        null
                    }
                } ?: emptyList()
                
                trySend(incomes)
            }
        
        awaitClose {
            listenerRegistration.remove()
        }
    }
    
    // Освежавање локалног кеша
    private fun refreshIncomes() {
        // Имплементација ће бити додата касније
    }
    
    companion object {
        @Volatile
        private var instance: IncomeRepository? = null
        
        fun getInstance(): IncomeRepository {
            return instance ?: synchronized(this) {
                instance ?: IncomeRepository().also { instance = it }
            }
        }
    }
} 