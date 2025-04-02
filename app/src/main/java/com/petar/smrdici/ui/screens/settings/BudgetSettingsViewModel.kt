package com.petar.smrdici.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Енумерација за валуте
enum class Currency(val code: String, val symbol: String) {
    RSD("RSD", "РСД"),
    EUR("EUR", "€"),
    USD("USD", "$"),
    GBP("GBP", "£")
}

// Енумерација за периоде
enum class Period(val value: String) {
    DAILY("Дневно"),
    WEEKLY("Недељно"),
    MONTHLY("Месечно"),
    YEARLY("Годишње"),
    CUSTOM("Прилагођено")
}

class BudgetSettingsViewModel : ViewModel() {
    private val _currency = MutableStateFlow(Currency.RSD)
    val currency: StateFlow<Currency> = _currency
    
    private val _period = MutableStateFlow(Period.MONTHLY)
    val period: StateFlow<Period> = _period
    
    // Додајемо подршку за прилагођени датум почетка периода
    private val _customPeriodStartDay = MutableStateFlow(1)
    val customPeriodStartDay: StateFlow<Int> = _customPeriodStartDay
    
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
        _period.value = period
        // Овде бисмо сачували подешавања у SharedPreferences или Datastore
    }
    
    // Функција за промену прилагођеног датума почетка периода
    fun setCustomPeriodStartDay(day: Int) {
        _customPeriodStartDay.value = day
        // Овде бисмо сачували подешавања у SharedPreferences или Datastore
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
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetSettingsViewModel::class.java)) {
                return BudgetSettingsViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 