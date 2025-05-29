package com.petar.smrdici.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.petar.smrdici.ui.screens.settings.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "smrdici_settings", Context.MODE_PRIVATE
    )
    
    private val _period = MutableStateFlow(getStoredPeriod())
    val period: StateFlow<Period> = _period.asStateFlow()
    
    private val _customPeriodStartDay = MutableStateFlow(getStoredCustomPeriodStartDay())
    val customPeriodStartDay: StateFlow<Int> = _customPeriodStartDay.asStateFlow()
    
    init {
        // Migration from budget_period to finance_period
        if (sharedPreferences.contains(KEY_PERIOD_OLD) && !sharedPreferences.contains(KEY_PERIOD)) {
            val oldPeriodValue = sharedPreferences.getString(KEY_PERIOD_OLD, Period.MONTHLY.name)
            sharedPreferences.edit {
                putString(KEY_PERIOD, oldPeriodValue)
                remove(KEY_PERIOD_OLD)
            }
            Log.d("SettingsRepository", "Migrated from budget_period to finance_period")
        }
    }
    
    fun setPeriod(period: Period) {
        Log.d("SettingsRepository", "Чувам период: $period")
        sharedPreferences.edit { putString(KEY_PERIOD, period.name) }
        _period.value = period
    }
    
    fun setCustomPeriodStartDay(day: Int) {
        Log.d("SettingsRepository", "Чувам дан почетка периода: $day")
        sharedPreferences.edit { putInt(KEY_CUSTOM_PERIOD_START_DAY, day) }
        _customPeriodStartDay.value = day
    }
    
    private fun getStoredPeriod(): Period {
        // Try to get from new key first, then fall back to old key
        val periodName = sharedPreferences.getString(KEY_PERIOD, null)
            ?: sharedPreferences.getString(KEY_PERIOD_OLD, Period.MONTHLY.name)
            
        return try {
            Period.valueOf(periodName ?: Period.MONTHLY.name)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Грешка при читању периода", e)
            Period.MONTHLY
        }
    }
    
    private fun getStoredCustomPeriodStartDay(): Int {
        return sharedPreferences.getInt(KEY_CUSTOM_PERIOD_START_DAY, 1)
    }
    
    companion object {
        private const val KEY_PERIOD_OLD = "budget_period"
        private const val KEY_PERIOD = "finance_period"
        private const val KEY_CUSTOM_PERIOD_START_DAY = "custom_period_start_day"
        
        @Volatile
        private var instance: SettingsRepository? = null
        
        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
        }
    }
} 