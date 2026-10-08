package com.petar.smrdici.utils

import android.util.Log

/**
 * Utility klasa za optimizovano logovanje
 */
object LogUtils {
    
    /**
     * Nivoi detaljnosti logovanja
     */
    enum class DetailLevel {
        MINIMAL,    // Only errors and important info
        NORMAL,     // Regular logging level
        VERBOSE     // Detailed logging for debugging
    }
    
    /**
     * Konfiguracione opcije za logovanje
     */
    object Config {
        // Main configuration options
        var ENABLE_DETAILED_LOGS = false
        var DETAIL_LEVEL = DetailLevel.NORMAL
        var MAX_ITEMS_TO_LOG = 5
        
        // Lists for filtering
        var DISABLED_TAGS = mutableSetOf<String>()
        var ENABLED_CATEGORIES = mutableSetOf(
            "finance", "income", "expense", "account", "auth",
            "budget", "transaction", "app", "ui", "repository"
        )
        
        // Categories that should show debug logs
        val DEBUG_CATEGORIES = setOf(
            "finance",      // Finance feature logs
            "income",       // Income-related logs
            "expense",      // Expense-related logs
            "account",      // Account-related logs
            "auth"         // Authentication logs
        )
        
        // Categories that should show verbose logs
        val VERBOSE_CATEGORIES = setOf(
            "finance"      // Show detailed finance logs
        )
        
        // Category management functions
        fun enableCategory(category: String) {
            ENABLED_CATEGORIES.add(category.lowercase())
        }
        
        fun disableCategory(category: String) {
            ENABLED_CATEGORIES.remove(category.lowercase())
        }

    }
    
    /**
     * Provera da li je dozvoljeno logovanje za dati tag
     */
    fun shouldLog(tag: String, category: String? = null): Boolean {
        if (tag in Config.DISABLED_TAGS) return false
        
        if (category != null && !Config.ENABLED_CATEGORIES.contains(category.lowercase())) {
            return false
        }
        return true
    }

    /**
     * Log verbose messages - najdetaljnije informacije
     */
    fun v(tag: String, message: String, category: String = "") {
        if (Config.ENABLE_DETAILED_LOGS && 
            Config.DETAIL_LEVEL == DetailLevel.VERBOSE && 
            shouldLog(tag, category) &&
            (category.isEmpty() || Config.VERBOSE_CATEGORIES.contains(category))) {
            Log.v(formatTag(tag, category), message)
        }
    }
    
    /**
     * Log informative messages - korisne informacije o stanju aplikacije
     */
    fun i(tag: String, message: String, category: String = "") {
        if (shouldLog(tag, category)) {
            Log.i(formatTag(tag, category), message)
        }
    }
    
    /**
     * Log debug messages - informacije korisne za debug, ali ne toliko bitne kao verbose
     */
    fun d(tag: String, message: String, category: String = "") {
        if (Config.ENABLE_DETAILED_LOGS && 
            Config.DETAIL_LEVEL >= DetailLevel.NORMAL && 
            shouldLog(tag, category) &&
            (category.isEmpty() || Config.DEBUG_CATEGORIES.contains(category))) {
            Log.d(formatTag(tag, category), message)
        }
    }
    
    /**
     * Log warning messages - potencijalni problemi ili neočekivane situacije
     */
    fun w(tag: String, message: String, category: String = "") {
        if (shouldLog(tag, category)) {
            Log.w(formatTag(tag, category), message)
        }
    }
    
    /**
     * Log error messages - ozbiljni problemi koji mogu uticati na funkcionalnost
     */
    fun e(tag: String, message: String, throwable: Throwable? = null, category: String = "") {
        if (shouldLog(tag, category)) {
            if (throwable != null) {
                Log.e(formatTag(tag, category), message, throwable)
            } else {
                Log.e(formatTag(tag, category), message)
            }
        }
    }

    private fun formatTag(tag: String, category: String): String {
        return if (category.isNotEmpty()) {
            "[$category] $tag"
        } else {
            tag
        }
    }

    public fun setDetailLevel(level: DetailLevel) {
        Config.DETAIL_LEVEL = level
    }
}