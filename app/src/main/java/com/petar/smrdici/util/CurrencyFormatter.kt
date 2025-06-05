package com.petar.smrdici.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Extension function to format a Double as a currency string
 * @param currencyCode The ISO 4217 currency code (e.g., "USD", "EUR", "RSD")
 * @return Formatted currency string with the appropriate symbol
 */
fun Double.formatCurrency(currencyCode: String): String {
    // Create a formatter for the specific currency
    val format = NumberFormat.getCurrencyInstance(getLocaleForCurrency(currencyCode))
    
    try {
        // Set the currency using the provided code
        format.currency = Currency.getInstance(currencyCode)
        
        // Special case for RSD which doesn't have a widely recognized symbol
        if (currencyCode == "RSD") {
            return String.format("%.2f РСД", this)
        }
        
        // Format the number as currency
        return format.format(this)
    } catch (e: Exception) {
        // Fallback if there's an error with the currency code
        return String.format("%.2f %s", this, currencyCode)
    }
}

/**
 * Helper function to get an appropriate locale for a given currency
 */
private fun getLocaleForCurrency(currencyCode: String): Locale {
    return when (currencyCode) {
        "USD" -> Locale.US
        "EUR" -> Locale.GERMANY
        "GBP" -> Locale.UK
        "RSD" -> Locale("sr", "RS")
        else -> Locale.getDefault()
    }
} 