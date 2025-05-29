package com.petar.smrdici.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AccountViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()
    
    // Shared accounts collection
    private val sharedAccountsCollection
        get() = firestore.collection("shared_accounts")
    
    init {
        loadAccounts()
    }
    
    private fun loadAccounts() {
        // Користимо нови scope за сваки позив
        val scope = viewModelScope
        scope.launch {
            _isLoading.value = true
            try {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    // Use shared_accounts collection instead of user-specific collection
                    val accountsSnapshot = sharedAccountsCollection
                        .get()
                        .await()
                    
                    val accountsList = accountsSnapshot.documents.mapNotNull { doc ->
                        try {
                            // Ручно мапирамо документ у Account објекат
                            val data = doc.data
                            if (data != null) {
                                Account(
                                    id = doc.id,
                                    userId = data["userId"] as? String ?: "",
                                    name = data["name"] as? String ?: "",
                                    balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                                    currency = data["currency"] as? String ?: "RSD",
                                    color = (data["color"] as? Number)?.toInt() ?: 0,
                                    isDefault = (data["isDefault"] as? Boolean) == true,
                                    type = try {
                                        AccountType.valueOf((data["type"] as? String) ?: AccountType.CASH.name)
                                    } catch (_: Exception) {
                                        AccountType.CASH
                                    }
                                )
                            } else null
                        } catch (e: Exception) {
                            Log.e("AccountViewModel", "Грешка при мапирању рачуна: ${doc.id}", e)
                            null
                        }
                    }
                    
                    _accounts.value = accountsList
                    
                    // Ако нема рачуна, креирамо подразумеване
                    if (accountsList.isEmpty()) {
                        createDefaultAccounts(userId)
                    }
                }
            } catch (e: Exception) {
                // Проверавамо да ли је грешка везана за отказивање корутине
                if (e.message?.contains("coroutine scope left the composition") == true) {
                    Log.d("AccountViewModel", "Композиција је напуштена, прекидамо учитавање")
                } else {
                    Log.e("AccountViewModel", "Грешка при учитавању рачуна", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun createDefaultAccounts(userId: String) {
        try {
            // Креирамо подразумеване рачуне
            val cashAccount = Account(
                id = UUID.randomUUID().toString(), // Генеришемо јединствени ID
                userId = userId,
                name = "Готовина",
                balance = 5000.0,
                currency = "RSD",
                color = 0, // Плава боја
                isDefault = true,
                type = AccountType.CASH
            )
            
            val bankAccount = Account(
                id = UUID.randomUUID().toString(), // Генеришемо јединствени ID
                userId = userId,
                name = "Текући рачун",
                balance = 25000.0,
                currency = "RSD",
                color = 1, // Зелена боја
                isDefault = false,
                type = AccountType.BANK
            )
            
            val creditCardAccount = Account(
                id = UUID.randomUUID().toString(), // Генеришемо јединствени ID
                userId = userId,
                name = "Кредитна картица",
                balance = -3000.0,
                currency = "RSD",
                color = 3, // Црвена боја
                isDefault = false,
                type = AccountType.CREDIT_CARD
            )
            
            // Save accounts to shared collection
            // Чувамо сваки рачун и користимо његов ID као ID документа
            sharedAccountsCollection.document(cashAccount.id).set(cashAccount).await()
            sharedAccountsCollection.document(bankAccount.id).set(bankAccount).await()
            sharedAccountsCollection.document(creditCardAccount.id).set(creditCardAccount).await()
            
            // Ажурирамо локалну листу рачуна
            _accounts.value = listOf(cashAccount, bankAccount, creditCardAccount)
            
        } catch (e: Exception) {
            Log.e("AccountViewModel", "Грешка при креирању подразумеваних рачуна", e)
        }
    }
    
    fun getAccountById(accountId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Прво проверамо да ли је рачун већ у локалној листи
                val localAccount = _accounts.value.find { it.id == accountId }
                if (localAccount != null) {
                    _currentAccount.value = localAccount
                    _isLoading.value = false
                    return@launch
                }
                
                // Ако није у локалној листи, учитавамо га из Firestore-а
                val accountDoc = sharedAccountsCollection
                    .document(accountId)
                    .get()
                    .await()
                
                if (accountDoc.exists()) {
                    try {
                        // Ручно мапирамо документ у Account објекат
                        val data = accountDoc.data
                        if (data != null) {
                            val account = Account(
                                id = accountDoc.id,
                                userId = data["userId"] as? String ?: "",
                                name = data["name"] as? String ?: "",
                                balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                                currency = data["currency"] as? String ?: "RSD",
                                color = (data["color"] as? Number)?.toInt() ?: 0,
                                isDefault = (data["isDefault"] as? Boolean) == true,
                                type = try {
                                    AccountType.valueOf((data["type"] as? String) ?: AccountType.CASH.name)
                                } catch (_: Exception) {
                                    AccountType.CASH
                                }
                            )
                            _currentAccount.value = account
                        }
                    } catch (e: Exception) {
                        Log.e("AccountViewModel", "Грешка при мапирању рачуна: ${accountDoc.id}", e)
                    }
                } else {
                    _currentAccount.value = null
                }
            } catch (e: Exception) {
                // Проверавамо да ли је грешка везана за отказивање корутине
                if (e.message?.contains("coroutine scope left the composition") == true) {
                    Log.d("AccountViewModel", "Композиција је напуштена, прекидамо учитавање")
                } else {
                    Log.e("AccountViewModel", "Грешка при учитавању рачуна", e)
                }
                _currentAccount.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addAccount(account: Account, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Генеришемо нови ID за рачун
                val newAccountId = UUID.randomUUID().toString()
                val newAccount = account.copy(id = newAccountId)
                
                Log.d("AccountViewModel", "Adding account: ${newAccount.name}, ID: $newAccountId")
                
                // Ако је ово први рачун или је означен као подразумевани
                if (_accounts.value.isEmpty() || newAccount.isDefault) {
                    // Постављамо све остале рачуне да нису подразумевани
                    setAllAccountsNonDefault()
                }
                
                // Чувамо нови рачун у shared collection
                sharedAccountsCollection
                    .document(newAccountId) // Користимо генерисани ID као ID документа
                    .set(newAccount)
                    .await()
                
                Log.d("AccountViewModel", "Account added successfully")
                
                // Ажурирамо локалну листу рачуна
                loadAccounts()
            } catch (e: Exception) {
                Log.e("AccountViewModel", "Error adding account", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateAccount(account: Account, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Ако је ажурирани рачун подразумевани, ажурирамо све остале рачуне
                if (account.isDefault) {
                    setAllAccountsNonDefault()
                }
                
                // Ажурирамо рачун у shared collection
                sharedAccountsCollection
                    .document(account.id)
                    .set(account)
                    .await()
                
                // Освежавамо локалну листу рачуна
                refreshAccounts()
                
                onComplete(true)
            } catch (e: Exception) {
                Log.e("AccountViewModel", "Грешка при ажурирању рачуна", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteAccount(accountId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Бришемо рачун из shared collection
                sharedAccountsCollection
                    .document(accountId)
                    .delete()
                    .await()
                
                // Ажурирамо локалну листу рачуна
                loadAccounts()
                
                onComplete(true)
            } catch (e: Exception) {
                Log.e("AccountViewModel", "Грешка при брисању рачуна", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Update this method to work with shared collection
    private suspend fun setAllAccountsNonDefault() {
        try {
            // Get all accounts from shared collection
            val accountsSnapshot = sharedAccountsCollection
                .get()
                .await()
            
            // Update each account to not be default
            for (doc in accountsSnapshot.documents) {
                sharedAccountsCollection
                    .document(doc.id)
                    .update("isDefault", false)
                    .await()
            }
        } catch (e: Exception) {
            Log.e("AccountViewModel", "Грешка при ажурирању подразумеваних рачуна", e)
        }
    }
    
    fun setDefaultAccount(accountId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Прво проверавамо да ли рачун постоји
                val accountDoc = sharedAccountsCollection
                    .document(accountId)
                    .get()
                    .await()
                
                if (!accountDoc.exists()) {
                    onComplete(false)
                    return@launch
                }
                
                // Ажурирамо све рачуне да нису подразумевани
                setAllAccountsNonDefault()
                
                // Постављамо изабрани рачун као подразумевани
                sharedAccountsCollection
                    .document(accountId)
                    .update("isDefault", true)
                    .await()
                
                loadAccounts()
                onComplete(true)
            } catch (e: Exception) {
                Log.e("AccountViewModel", "Грешка при постављању подразумеваног рачуна", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Додајте јавну функцију за освежавање рачуна
    fun refreshAccounts() {
        loadAccounts()
    }
    
    // Factory klasа za kreiranje ViewModel-a
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
                return AccountViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 