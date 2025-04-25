package com.petar.smrdici.util

/**
 * Помоћна класа за конверзију између различитих валута
 */
object CurrencyConverter {
    // Константни курсеви валута у односу на RSD (1 страна валута = X RSD)
    private val RATES_TO_RSD = mapOf(
        "RSD" to 1.0,
        "EUR" to 117.5,  // 1 EUR = 117.5 RSD
        "USD" to 108.2,  // 1 USD = 108.2 RSD
        "GBP" to 137.6   // 1 GBP = 137.6 RSD
    )

    /**
     * Конвертује износ из једне валуте у другу
     * @param amount износ који се конвертује
     * @param fromCurrency изворна валута
     * @param toCurrency циљна валута
     * @return конвертован износ у циљној валути
     */
    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if (fromCurrency == toCurrency) {
            return amount
        }

        // Претварамо све у RSD
        val amountInRsd = amount * (RATES_TO_RSD[fromCurrency] ?: 1.0)

        // Затим из RSD у циљну валуту
        return amountInRsd / (RATES_TO_RSD[toCurrency] ?: 1.0)
    }

    /**
     * Конвертује износ у RSD валуту
     * @param amount износ који се конвертује
     * @param fromCurrency изворна валута
     * @return конвертован износ у RSD валути
     */
    fun convertToRsd(amount: Double, fromCurrency: String): Double {
        return convert(amount, fromCurrency, "RSD")
    }

    /**
     * Конвертује износ из RSD валуте у другу валуту
     * @param amount износ у RSD који се конвертује
     * @param toCurrency циљна валута
     * @return конвертован износ у циљној валути
     */
    fun convertFromRsd(amount: Double, toCurrency: String): Double {
        return convert(amount, "RSD", toCurrency)
    }
} 