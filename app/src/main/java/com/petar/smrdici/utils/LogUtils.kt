package com.petar.smrdici.utils

import android.util.Log
import java.util.*

/**
 * Utility klasa za optimizovano logovanje
 */
object LogUtils {
    
    /**
     * Nivoi detaljnosti logovanja
     */
    enum class DetailLevel {
        NONE,       // Bez detaljnog logovanja
        MINIMAL,    // Samo osnovne informacije
        NORMAL,     // Standardni nivo detalja
        VERBOSE     // Maksimalan nivo detalja
    }
    
    /**
     * Konfiguracione opcije za logovanje
     */
    object Config {
        // Glavni prekidač za detaljno logovanje - lako se menja između build varijanti
        var ENABLE_DETAILED_LOGS = false
        
        // Nivo detaljnosti logova
        var DETAIL_LEVEL = DetailLevel.NORMAL
        
        // Maksimalan broj stavki za logovanje u kolekciji
        var MAX_ITEMS_TO_LOG = 5
        
        // Lista tagova za koje je isključeno logovanje
        var DISABLED_TAGS = mutableSetOf<String>()
        
        // Kategorije logova koje će biti prikazane
        var ENABLED_CATEGORIES = mutableSetOf(
            "expense", "income", "budget", "transaction", "app", "ui", "repository"
        )
        
        // Brzo uključivanje/isključivanje određenih kategorija logova
        fun enableCategory(category: String) {
            ENABLED_CATEGORIES.add(category.lowercase())
        }
        
        fun disableCategory(category: String) {
            ENABLED_CATEGORIES.remove(category.lowercase())
        }
        
        // Brzo uključivanje/isključivanje određenih tagova
        fun disableTag(tag: String) {
            DISABLED_TAGS.add(tag)
        }
        
        fun enableTag(tag: String) {
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
    fun v(tag: String, message: String, category: String? = null) {
        if (Config.ENABLE_DETAILED_LOGS && Config.DETAIL_LEVEL == DetailLevel.VERBOSE && shouldLog(tag, category)) {
            Log.v(tag, message)
        }
    }
    
    /**
     * Log informative messages - korisne informacije o stanju aplikacije
     */
    fun i(tag: String, message: String, category: String? = null) {
        if (shouldLog(tag, category)) {
            Log.i(tag, message)
        }
    }
    
    /**
     * Log debug messages - informacije korisne za debug, ali ne toliko bitne kao verbose
     */
    fun d(tag: String, message: String, category: String? = null) {
        if (Config.ENABLE_DETAILED_LOGS && shouldLog(tag, category)) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Log warning messages - potencijalni problemi ili neočekivane situacije
     */
    fun w(tag: String, message: String, category: String? = null) {
        if (shouldLog(tag, category)) {
            Log.w(tag, message)
        }
    }
    
    /**
     * Log error messages - ozbiljni problemi koji mogu uticati na funkcionalnost
     */
    fun e(tag: String, message: String, throwable: Throwable? = null, category: String? = null) {
        if (shouldLog(tag, category)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
    
    /**
     * Log a collection of items with a limit on the number of items logged
     */
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
}
