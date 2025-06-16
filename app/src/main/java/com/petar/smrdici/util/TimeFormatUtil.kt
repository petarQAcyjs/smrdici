package com.petar.smrdici.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.util.Locale

/**
 * Utility class to handle time format settings
 */
object TimeFormatUtil {
    private const val TAG = "TimeFormatUtil"
    
    /**
     * Forces 24-hour time format for the application
     */
    fun force24HourFormat(context: Context) {
        try {
            // Set Serbian locale as default (uses 24-hour format)
            val locale = Locale.forLanguageTag("sr")
            Locale.setDefault(locale)
            
            val config = Configuration(context.resources.configuration)
            
            // Apply the locale configuration using the appropriate API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(locale)
                
                // Create and apply the configuration context
                val newContext = context.createConfigurationContext(config)
                
                // Apply the layout direction for RTL/LTR support
                config.setLayoutDirection(locale)
            } else {
                // For older Android versions
                @Suppress("DEPRECATION")
                config.locale = locale
                
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
            }
            
            // Check if we succeeded
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            Log.d(TAG, "24-hour format applied: $is24Hour")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting 24-hour format", e)
        }
    }
} 