package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Енумерација за валуте
enum class Currency(val code: String, val symbol: String, val value: String) {
    RSD("RSD", "РСД", "Динар (RSD)"),
    EUR("EUR", "€", "Евро (EUR)"),
    USD("USD", "$", "Долар (USD)"),
    GBP("GBP", "£", "Фунта (GBP)")
}

// Енумерација за периоде
enum class Period(val value: String) {
    DAILY("Дневно"),
    WEEKLY("Недељно"),
    MONTHLY("Месечно"),
    YEARLY("Годишње"),
    CUSTOM("Прилагођено"),
    ALL("Све")
}

class BudgetSettingsViewModel(private val context: Context) : ViewModel() {
    private val settingsRepository = SettingsRepository.getInstance(context)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _currency = MutableStateFlow(Currency.RSD)
    val currency: StateFlow<Currency> = _currency
    
    // Користимо период из репозиторијума
    val period: StateFlow<Period> = settingsRepository.period
    
    // Користимо прилагођени период из репозиторијума
    val customPeriodStartDay: StateFlow<Int> = settingsRepository.customPeriodStartDay
    
    // Додајемо подршку за рачуне
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts
    
    // Привремено додајемо неке тест рачуне
    init {
        _accounts.value = listOf(
            Account(
                name = "Готовина",
                balance = 5000.0,
                currency = "RSD",
                color = 0xFF4CAF50.toInt(),
                isDefault = true,
                type = AccountType.CASH
            ),
            Account(
                name = "Текући рачун",
                balance = 25000.0,
                currency = "RSD",
                color = 0xFF2196F3.toInt(),
                isDefault = false,
                type = AccountType.BANK
            ),
            Account(
                name = "Кредитна картица",
                balance = -3000.0,
                currency = "RSD",
                color = 0xFFF44336.toInt(),
                isDefault = false,
                type = AccountType.CREDIT_CARD
            )
        )
    }
    
    // Функција за промену валуте
    fun setCurrency(currency: Currency) {
        _currency.value = currency
        // Овде бисмо сачували подешавања у SharedPreferences или Datastore
    }
    
    // Функција за промену периода
    fun setPeriod(period: Period) {
        Log.d("BudgetSettingsViewModel", "Постављам период: $period")
        settingsRepository.setPeriod(period)
    }
    
    // Функција за промену прилагођеног датума почетка периода
    fun setCustomPeriodStartDay(day: Int) {
        Log.d("BudgetSettingsViewModel", "Постављам дан почетка периода: $day")
        settingsRepository.setCustomPeriodStartDay(day)
    }
    
    // Функција за додавање новог рачуна
    fun addAccount(account: Account) {
        val currentAccounts = _accounts.value.toMutableList()
        
        // Ако је нови рачун подразумевани, уклањамо подразумевани статус са осталих рачуна
        if (account.isDefault) {
            val updatedAccounts = currentAccounts.map { 
                if (it.isDefault) it.copy(isDefault = false) else it 
            }
            currentAccounts.clear()
            currentAccounts.addAll(updatedAccounts)
        }
        
        currentAccounts.add(account)
        _accounts.value = currentAccounts
    }
    
    // Функција за ажурирање постојећег рачуна
    fun updateAccount(account: Account) {
        val currentAccounts = _accounts.value.toMutableList()
        val index = currentAccounts.indexOfFirst { it.id == account.id }
        
        if (index != -1) {
            // Ако је ажурирани рачун подразумевани, уклањамо подразумевани статус са осталих рачуна
            if (account.isDefault) {
                val updatedAccounts = currentAccounts.map { 
                    if (it.id != account.id && it.isDefault) it.copy(isDefault = false) else it 
                }
                currentAccounts.clear()
                currentAccounts.addAll(updatedAccounts)
            }
            
            currentAccounts[index] = account
            _accounts.value = currentAccounts
        }
    }
    
    // Функција за брисање рачуна
    fun deleteAccount(accountId: String) {
        val currentAccounts = _accounts.value.toMutableList()
        val accountToDelete = currentAccounts.find { it.id == accountId }
        
        if (accountToDelete != null) {
            currentAccounts.remove(accountToDelete)
            
            // Ако је обрисани рачун био подразумевани, постављамо први преостали рачун као подразумевани
            if (accountToDelete.isDefault && currentAccounts.isNotEmpty()) {
                val firstAccount = currentAccounts[0]
                currentAccounts[0] = firstAccount.copy(isDefault = true)
            }
            
            _accounts.value = currentAccounts
        }
    }
    
    // Функција за постављање подразумеваног рачуна
    fun setDefaultAccount(accountId: String) {
        val currentAccounts = _accounts.value.toMutableList()
        val updatedAccounts = currentAccounts.map { account ->
            when (account.id) {
                accountId -> account.copy(isDefault = true)
                else -> if (account.isDefault) account.copy(isDefault = false) else account
            }
        }
        
        currentAccounts.clear()
        currentAccounts.addAll(updatedAccounts)
        _accounts.value = currentAccounts
    }
    
    // Factory класа за креирање ViewModel-а
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetSettingsViewModel::class.java)) {
                return BudgetSettingsViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 