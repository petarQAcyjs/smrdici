package com.petar.smrdici.utils

import android.util.Log
import java.util.*

/**
 * Utility klasa za optimizovano logovanje
 */
public object LogUtils {
    
    /**
     * Nivoi detaljnosti logovanja
     */
    public enum class DetailLevel {
        NONE,       // No logging
        MINIMAL,    // Only errors and important info
        NORMAL,     // Regular logging level
        VERBOSE     // Detailed logging for debugging
    }
    
    /**
     * Konfiguracione opcije za logovanje
     */
    public object Config {
        // Main configuration options
        public var ENABLE_DETAILED_LOGS = false
        public var DETAIL_LEVEL = DetailLevel.NORMAL
        public var MAX_ITEMS_TO_LOG = 5
        
        // Lists for filtering
        public var DISABLED_TAGS = mutableSetOf<String>()
        public var ENABLED_CATEGORIES = mutableSetOf(
            "finance", "income", "expense", "account", "auth",
            "budget", "transaction", "app", "ui", "repository"
        )
        
        // Categories that should show debug logs
        public val DEBUG_CATEGORIES = setOf(
            "finance",      // Finance feature logs
            "income",       // Income-related logs
            "expense",      // Expense-related logs
            "account",      // Account-related logs
            "auth"         // Authentication logs
        )
        
        // Categories that should show verbose logs
        public val VERBOSE_CATEGORIES = setOf(
            "finance"      // Show detailed finance logs
        )
        
        // Category management functions
        public fun enableCategory(category: String) {
            ENABLED_CATEGORIES.add(category.lowercase())
        }
        
        public fun disableCategory(category: String) {
            ENABLED_CATEGORIES.remove(category.lowercase())
        }
        
        // Tag management functions
        public fun disableTag(tag: String) {
            DISABLED_TAGS.add(tag)
        }
        
        public fun enableTag(tag: String) {
            DISABLED_TAGS.remove(tag)
        }
    }
    
    /**
     * Provera da li je dozvoljeno logovanje za dati tag
     */
    public fun shouldLog(tag: String, category: String? = null): Boolean {
        if (tag in Config.DISABLED_TAGS) return false
        
        if (category != null && !Config.ENABLED_CATEGORIES.contains(category.lowercase())) {
            return false
        }
        return true
    }

    /**
     * Log verbose messages - najdetaljnije informacije
     */
    public fun v(tag: String, message: String, category: String = "") {
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
    public fun i(tag: String, message: String, category: String = "") {
        if (Config.DETAIL_LEVEL >= DetailLevel.MINIMAL && shouldLog(tag, category)) {
            Log.i(formatTag(tag, category), message)
        }
    }
    
    /**
     * Log debug messages - informacije korisne za debug, ali ne toliko bitne kao verbose
     */
    public fun d(tag: String, message: String, category: String = "") {
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
    public fun w(tag: String, message: String, category: String = "") {
        if (shouldLog(tag, category)) {
            Log.w(formatTag(tag, category), message)
        }
    }
    
    /**
     * Log error messages - ozbiljni problemi koji mogu uticati na funkcionalnost
     */
    public fun e(tag: String, message: String, throwable: Throwable? = null, category: String = "") {
        if (shouldLog(tag, category)) {
            if (throwable != null) {
                Log.e(formatTag(tag, category), message, throwable)
            } else {
                Log.e(formatTag(tag, category), message)
            }
        }
    }
    
    /**
     * Log a collection of items with a limit on the number of items logged
     */
    @JvmName("logCollection")
    public inline fun <T> logCollection(
        tag: String,
        collection: Collection<T>,
        prefix: String = "",
        category: String? = null,
        detailLevel: DetailLevel = DetailLevel.NORMAL,
        transform: (T) -> String
    ) {
        if (!Config.ENABLE_DETAILED_LOGS || 
            detailLevel.ordinal > Config.DETAIL_LEVEL.ordinal || 
            !shouldLog(tag, category)) return
        
        val size = collection.size
        val maxItems = when (Config.DETAIL_LEVEL) {
            DetailLevel.VERBOSE -> Config.MAX_ITEMS_TO_LOG * 2
            DetailLevel.NORMAL -> Config.MAX_ITEMS_TO_LOG
            DetailLevel.MINIMAL -> 3
            DetailLevel.NONE -> return
        }
        
        val itemsToLog = collection.take(maxItems)
        
        d(tag, "$prefix Ukupno ${collection.size} stavki:")
        
        itemsToLog.forEachIndexed { index, item ->
            d(tag, "$prefix Stavka #${index + 1}: ${transform(item)}")
        }
        
        if (size > maxItems) {
            d(tag, "$prefix ...i još ${size - maxItems} stavki")
        }
    }
    
    /**
     * Log numeric statistics for a collection of numeric values
     */
    @JvmName("logNumericStats")
    public fun logNumericStats(
        tag: String,
        values: Collection<Double>,
        prefix: String = "",
        category: String? = null,
        detailLevel: DetailLevel = DetailLevel.NORMAL
    ) {
        if (!Config.ENABLE_DETAILED_LOGS || 
            detailLevel.ordinal > Config.DETAIL_LEVEL.ordinal || 
            !shouldLog(tag, category) || 
            values.isEmpty()) return
        
        val sum = values.sum()
        val avg = sum / values.size
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        
        val count = values.size
        
        // Format the statistics for better readability
        val formattedStats = String.format(
            Locale.getDefault(),
            "$prefix Statistika (stavki: %d): ukupno=%.2f, prosek=%.2f, min=%.2f, max=%.2f",
            count, sum, avg, min, max
        )
        
        d(tag, formattedStats)
        
        // If the detail level is VERBOSE, add the distribution of values
        if (detailLevel == DetailLevel.VERBOSE && Config.DETAIL_LEVEL == DetailLevel.VERBOSE) {
            // Group the values into segments
            val segments = 5
            val range = if (max > min) max - min else 1.0
            val step = range / segments
            
            val distribution = Array(segments) { 0 }
            
            for (value in values) {
                val index = if (value >= max) segments - 1 
                           else ((value - min) / step).toInt().coerceIn(0, segments - 1)
                distribution[index]++
            }
            
            // Log the distribution
            for (i in 0 until segments) {
                val lowerBound = min + i * step
                val upperBound = if (i < segments - 1) min + (i + 1) * step else max
                val percentage = (distribution[i] * 100.0 / count).toInt()
                
                d(tag, String.format(
                    Locale.getDefault(),
                    "$prefix Opseg %.2f-%.2f: %d stavki (%d%%)",
                    lowerBound, upperBound, distribution[i], percentage
                ))
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
