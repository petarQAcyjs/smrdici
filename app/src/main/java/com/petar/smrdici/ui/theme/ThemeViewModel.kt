package com.petar.smrdici.ui.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(
    context: Context
) : ViewModel() {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    
    // Користимо applicationContext уместо директног context
    private val appContext = context.applicationContext
    private val sharedPreferences = appContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    init {
        loadSavedTheme()
    }

    fun loadSavedTheme() {
        viewModelScope.launch {
            val savedTheme = sharedPreferences
                .getString("theme_mode", ThemeMode.SYSTEM.name)
            _themeMode.value = ThemeMode.valueOf(savedTheme ?: ThemeMode.SYSTEM.name)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            _themeMode.value = mode
            // Čuvamo izbor teme
            sharedPreferences.edit().apply {
                putString("theme_mode", mode.name)
                apply()
            }
        }
    }
}

class ThemeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThemeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ThemeViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 