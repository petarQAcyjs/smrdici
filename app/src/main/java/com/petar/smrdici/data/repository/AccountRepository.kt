package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AccountRepository private constructor() {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: Flow<List<Account>> = _accounts.asStateFlow()
    
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("No authenticated user")

    private val userAccountsCollection
        get() = firestore.collection("users").document(currentUserId).collection("accounts")
    
    init {
        loadAccounts()
        Log.d(TAG, "AccountRepository inicijalizovan - nova verzija!")
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
        userAccountsCollection.document(account.id).set(account).await()
    }
    
    suspend fun updateAccount(account: Account) {
        userAccountsCollection.document(account.id).set(account).await()
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
            
            // Create a new account with the updated ID and balance
            val updatedAccount = account.copy(
                id = accountId,
                balance = account.balance + amount
            )
            
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
        userAccountsCollection.document(accountId).delete().await()
    }
    
    // Додајемо нову методу за трансфер новца између рачуна
    suspend fun transferFunds(
        sourceAccountId: String, 
        destinationAccountId: String, 
        amount: Double,
        description: String = "Трансфер новца"
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Покрећем трансфер новца: са $sourceAccountId на $destinationAccountId, износ: $amount")
            
            val userId = auth.currentUser?.uid ?: return Result.failure(
                IllegalStateException("Корисник није пријављен")
            )
            
            // Проверавамо да ли су рачуни валидни и има ли довољно средстава
            val sourceAccount = getAccountByIdSuspend(sourceAccountId)
            val destinationAccount = getAccountByIdSuspend(destinationAccountId)
            
            if (sourceAccount == null) {
                return Result.failure(NoSuchElementException("Изворни рачун није пронађен"))
            }
            
            if (destinationAccount == null) {
                return Result.failure(NoSuchElementException("Циљни рачун није пронађен"))
            }
            
            // Проверавамо да ли има довољно средстава
            if (sourceAccount.balance < amount) {
                return Result.failure(IllegalArgumentException("Недовољно средстава на изворном рачуну"))
            }
            
            // Креирамо трансакцију трансфера у Firebase
            val transferId = UUID.randomUUID().toString()
            val transfer = mapOf(
                "id" to transferId,
                "sourceAccountId" to sourceAccountId,
                "destinationAccountId" to destinationAccountId,
                "amount" to amount,
                "description" to description,
                "timestamp" to Timestamp.now(),
                "userId" to userId
            )
            
            // Користимо трансакцију да би се све операције извршиле атомично
            val db = firestore
            db.runTransaction { transaction ->
                // 1. Скидамо средства са изворног рачуна
                val sourceAccountRef = db.collection("users").document(userId)
                    .collection("accounts").document(sourceAccountId)
                
                val sourceAccount = transaction.get(sourceAccountRef).toObject(Account::class.java)
                    ?: throw IllegalStateException("Не могу да добавим изворни рачун")
                
                // Проверавамо баланс још једном у трансакцији
                if (sourceAccount.balance < amount) {
                    throw IllegalArgumentException("Недовољно средстава на изворном рачуну")
                }
                
                val updatedSourceAccount = sourceAccount.copy(balance = sourceAccount.balance - amount)
                transaction.set(sourceAccountRef, updatedSourceAccount)
                
                // 2. Додајемо средства на циљни рачун
                val destAccountRef = db.collection("users").document(userId)
                    .collection("accounts").document(destinationAccountId)
                
                val destAccount = transaction.get(destAccountRef).toObject(Account::class.java)
                    ?: throw IllegalStateException("Не могу да добавим циљни рачун")
                
                val updatedDestAccount = destAccount.copy(balance = destAccount.balance + amount)
                transaction.set(destAccountRef, updatedDestAccount)
                
                // 3. Додајемо запис о трансферу
                val transferRef = db.collection("users").document(userId)
                    .collection("transfers").document(transferId)
                
                transaction.set(transferRef, transfer)
                
                // Враћамо успех
                null
            }.await()
            
            // Освежавамо локални кеш рачуна
            loadAccounts()
            
            Log.d(TAG, "Трансфер успешно обављен")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при трансферу новца", e)
            Result.failure(e)
        }
    }
    
    // Renamed to avoid conflict and clarify usage
    private suspend fun getAccountByIdSuspend(accountId: String): Account? {
        try {
            val userId = auth.currentUser?.uid ?: return null
            
            val docSnapshot = firestore.collection("users").document(userId)
                .collection("accounts").document(accountId)
                .get().await()
            
            if (!docSnapshot.exists()) {
                return null
            }
            
            return docSnapshot.toObject(Account::class.java)?.copy(id = accountId)
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при добављању рачуна по ID-у", e)
            return null
        }
    }
    
    // Добављање историје трансфера
    suspend fun getTransferHistory(): List<Map<String, Any>> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            
            val snapshot = firestore.collection("users").document(userId)
                .collection("transfers")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.data
            }
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при добављању историје трансфера", e)
            emptyList()
        }
    }
    
    // Flow version for UI updates
    fun getAccountByIdFlow(accountId: String): Flow<Account?> = flow {
        val userId = auth.currentUser?.uid ?: run {
            emit(null)
            return@flow
        }
        
        val docSnapshot = userAccountsCollection.document(accountId).get().await()
        emit(docSnapshot.toObject(Account::class.java)?.copy(id = accountId))
    }
    
    /**
     * Retrieves current balances for all accounts
     * @return Map of account IDs to their current balances
     */
    suspend fun getAccountBalances(): Map<String, Double> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyMap()
            
            val snapshot = firestore.collection("users").document(userId)
                .collection("accounts")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val account = doc.toObject(Account::class.java)
                if (account != null) {
                    Pair(doc.id, account.balance)
                } else null
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving account balances", e)
            emptyMap()
        }
    }
    
    companion object {
        private const val TAG = "AccountRepository"
        
        @Volatile
        private var instance: AccountRepository? = null
        
        /**
         * Vraća instancu AccountRepository-ja.
         * 
         * @param context Parametar se trenutno ne koristi, ali je zadržan zbog
         * kompatibilnosti sa postojećim kodom koji očekuje ovu signaturu i zbog
         * konzistentnosti sa drugim repozitorijumima u aplikaciji.
         * @return instanca AccountRepository-ja
         */
        @Suppress("UNUSED_PARAMETER")
        fun getInstance(context: Context? = null): AccountRepository {
            return instance ?: synchronized(this) {
                instance ?: AccountRepository().also { instance = it }
            }
        }
    }
} 