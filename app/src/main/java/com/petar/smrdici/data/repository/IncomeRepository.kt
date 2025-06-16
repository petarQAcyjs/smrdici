package com.petar.smrdici.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Income
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.tasks.await
import java.util.Date

class IncomeRepository private constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    
    // Референца на AccountRepository
    private var accountRepository: AccountRepository? = null
    
    fun setAccountRepository(accountRepo: AccountRepository) {
        this.accountRepository = accountRepo
    }
    
    // Shared incomes collection
    private val sharedIncomesCollection
        get() = firestore.collection("shared_incomes")
    
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
            
            Result.success(incomeToUpdate)
        } catch (e: Exception) {
            LogUtils.e("IncomeRepository", "Greška pri ažuriranju prihoda", e, category = "income")
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