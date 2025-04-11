package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.NoSuchElementException

class AccountRepository private constructor(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: Flow<List<Account>> = _accounts.asStateFlow()
    
    init {
        loadAccounts()
    }
    
    fun loadAccounts() {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("users").document(userId)
            .collection("accounts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Грешка при учитавању рачуна", error)
                    return@addSnapshotListener
                }
                
                val accountsList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Account::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                _accounts.value = accountsList
            }
    }
    
    suspend fun getAllAccounts(): List<Account> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            
            val snapshot = firestore.collection("users").document(userId)
                .collection("accounts")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Account::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при добављању свих рачуна", e)
            emptyList()
        }
    }
    
    suspend fun addAccount(account: Account) {
        try {
            val userId = auth.currentUser?.uid ?: return
            
            val accountData = account.copy(
                id = account.id.ifEmpty { UUID.randomUUID().toString() }
            )
            
            firestore.collection("users").document(userId)
                .collection("accounts")
                .document(accountData.id)
                .set(accountData)
                .await()
            
            loadAccounts()
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при додавању рачуна", e)
        }
    }
    
    suspend fun updateAccount(account: Account) {
        try {
            val userId = auth.currentUser?.uid ?: return
            
            firestore.collection("users").document(userId)
                .collection("accounts")
                .document(account.id)
                .set(account)
                .await()
            
            loadAccounts()
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при ажурирању рачуна", e)
        }
    }
    
    /**
     * Ажурира баланс рачуна након додавања трошка или прихода
     * @param accountId ID рачуна чији се баланс ажурира
     * @param amount износ за који се мења баланс (може бити позитиван или негативан)
     * @return успешно ажурирање или грешка
     */
    suspend fun updateAccountBalance(accountId: String, amount: Double): Result<Account> {
        return try {
            Log.d(TAG, "Ажурирам баланс рачуна: $accountId за износ: $amount")
            val userId = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            
            // Прво добављамо тренутно стање рачуна
            val docRef = firestore.collection("users").document(userId)
                .collection("accounts")
                .document(accountId)
            
            val docSnapshot = docRef.get().await()
            
            if (!docSnapshot.exists()) {
                return Result.failure(NoSuchElementException("Рачун са ID: $accountId није пронађен"))
            }
            
            // Конвертујемо у објекат Account
            val account = docSnapshot.toObject(Account::class.java)
                ?: return Result.failure(IllegalStateException("Не могу да конвертујем документ у Account"))
            
            // Додатно постављамо ID
            account.id = accountId
            
            // Ажурирамо баланс
            val updatedAccount = account.copy(balance = account.balance + amount)
            
            // Чувамо назад у Firestore
            docRef.set(updatedAccount).await()
            
            // Освежавамо локални кеш
            loadAccounts()
            
            Result.success(updatedAccount)
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при ажурирању баланса рачуна", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteAccount(accountId: String) {
        try {
            val userId = auth.currentUser?.uid ?: return
            
            firestore.collection("users").document(userId)
                .collection("accounts")
                .document(accountId)
                .delete()
                .await()
            
            loadAccounts()
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при брисању рачуна", e)
        }
    }
    
    suspend fun deleteAllAccounts() {
        try {
            val userId = auth.currentUser?.uid ?: return
            
            val snapshot = firestore.collection("users").document(userId)
                .collection("accounts")
                .get()
                .await()
            
            for (document in snapshot.documents) {
                firestore.collection("users").document(userId)
                    .collection("accounts")
                    .document(document.id)
                    .delete()
                    .await()
            }
            
            loadAccounts()
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при брисању свих рачуна", e)
        }
    }
    
    companion object {
        private const val TAG = "AccountRepository"
        
        @Volatile
        private var instance: AccountRepository? = null
        
        fun getInstance(context: Context): AccountRepository {
            return instance ?: synchronized(this) {
                instance ?: AccountRepository(context).also { instance = it }
            }
        }
    }
} 