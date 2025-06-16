package com.petar.smrdici.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * A wrapper for TimePicker that enforces 24-hour format
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerWrapper(
    state: TimePickerState
) {
    val context = LocalContext.current
    
    // Apply Serbian locale context to force 24-hour format
    createContextWithSerbianLocale(context)
    
    // Use the standard TimePicker with Serbian locale context
    TimePicker(state = state)
}

/**
 * Creates a context with Serbian locale to enforce 24-hour format
 */
private fun createContextWithSerbianLocale(baseContext: Context): Context {
    val locale = Locale.forLanguageTag("sr")
    Locale.setDefault(locale)
    
    val config = Configuration(baseContext.resources.configuration)
    
    config.setLocale(locale)
    return baseContext.createConfigurationContext(config)
} 