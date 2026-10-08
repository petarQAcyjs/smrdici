package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AccountRepository private constructor() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: Flow<List<Account>> = _accounts.asStateFlow()

    // Vraća null ako korisnik nije prijavljen (umesto da baca izuzetak)
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // Shared accounts collection
    private val sharedAccountsCollection
        get() = firestore.collection("shared_accounts")

    init {
        loadAccounts()
        Log.d(TAG, "AccountRepository inicijalizovan - nova verzija!")
    }

    fun loadAccounts() {
        // Ako nema prijavljenog korisnika, ne upućujemo zahtev ka Firestore-u
        if (currentUserId == null) {
            Log.d(TAG, "Nema prijavljenog korisnika, preskačem loadAccounts.")
            _accounts.value = emptyList()
            return
        }

        sharedAccountsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Грешка при учитавању рачуна", error)
                    _accounts.value = emptyList()
                    return@addSnapshotListener
                }

                val accountsList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Account::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                if (accountsList.isEmpty()) {
                    coroutineScope.launch {
                        createDefaultAccountIfNeeded()
                    }
                } else {
                    _accounts.value = accountsList
                }
            }
    }

    // Kreira podrazumevani račun samo ako korisnik postoji
    private suspend fun createDefaultAccountIfNeeded() {
        val userId = currentUserId ?: run {
            Log.d(TAG, "Nema ulogovanog korisnika za kreiranje podrazumevanog računa.")
            return
        }

        try {
            val snapshot = sharedAccountsCollection.get().await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No accounts found, creating default account")

                val defaultAccountId = UUID.randomUUID().toString()
                val defaultAccount = Account(
                    id = defaultAccountId,
                    userId = userId,
                    name = "Glavni račun",
                    balance = 0.0,
                    currency = Account.DEFAULT_CURRENCY,
                    color = 0xFF2196F3.toInt(), // Material Blue
                    isDefault = true,
                    type = AccountType.CASH,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                addAccount(defaultAccount)
                _accounts.value = listOf(defaultAccount)

                Log.d(TAG, "Created default account: $defaultAccountId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/creating default account", e)
        }
    }

    suspend fun addAccount(account: Account) {
        sharedAccountsCollection.document(account.id).set(account).await()
    }

    suspend fun updateAccountBalance(accountId: String, amount: Double): Result<Account> {
        return try {
            Log.d(TAG, "Ажурирам баланс рачуна: $accountId за износ: $amount")

            val docRef = sharedAccountsCollection.document(accountId)
            val docSnapshot = docRef.get().await()

            if (!docSnapshot.exists()) {
                return Result.failure(NoSuchElementException("Рачун са ID: $accountId није пронађен"))
            }

            val account = docSnapshot.toObject(Account::class.java)
                ?: return Result.failure(IllegalStateException("Не могу да конвертујем документ у Account"))

            val updatedAccount = account.copy(
                id = accountId,
                balance = account.balance + amount
            )

            docRef.set(updatedAccount).await()
            loadAccounts()

            Result.success(updatedAccount)
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при ажурирању баланса рачуна", e)
            Result.failure(e)
        }
    }

    suspend fun getAccountBalances(): Map<String, Double> {
        return try {
            val snapshot = sharedAccountsCollection
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

        @Suppress("UNUSED_PARAMETER")
        fun getInstance(context: Context? = null): AccountRepository {
            return instance ?: synchronized(this) {
                instance ?: AccountRepository().also { instance = it }
            }
        }
    }
}