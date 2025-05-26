package com.petar.smrdici.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.petar.smrdici.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Енумерација за валуте
enum class Currency(val code: String, val symbol: String, val value: String) {
    RSD("RSD", "РСД", "Динар (RSD)"),
    EUR("EUR", "€", "Евро (EUR)"),
    USD("USD", "$", "Долар (USD)"),
    GBP("GBP", "£", "Фунта (GBP)")
}

class FinanceSettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val _currency = MutableStateFlow(Currency.RSD)
    val currency: StateFlow<Currency> = _currency
    
    // Користимо период из репозиторијума
    val period: StateFlow<Period> = settingsRepository.period
    
    // Користимо прилагођени период из репозиторијума
    val customPeriodStartDay: StateFlow<Int> = settingsRepository.customPeriodStartDay
    
    // Функција за промену валуте
    fun setCurrency(currency: Currency) {
        _currency.value = currency
        // Овде бисмо сачували подешавања у SharedPreferences или Datastore
    }
    
    // Функција за промену периода
    fun setPeriod(period: Period) {
        Log.d("FinanceSettingsViewModel", "Постављам период: $period")
        settingsRepository.setPeriod(period)
    }
    
    // Функција за промену прилагођеног датума почетка периода
    fun setCustomPeriodStartDay(day: Int) {
        Log.d("FinanceSettingsViewModel", "Постављам дан почетка периода: $day")
        settingsRepository.setCustomPeriodStartDay(day)
    }
    
    // Factory класа за креирање ViewModel-а
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceSettingsViewModel::class.java)) {
                // Kreiramo repository u factory metodi umesto da skladištimo context
                val settingsRepository = SettingsRepository.getInstance(context)
                return FinanceSettingsViewModel(settingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 