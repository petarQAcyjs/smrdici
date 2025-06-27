package com.petar.smrdici.data.model

import java.util.Locale

/**
 * Enumeracija koja definiše sve moguće kategorije prihoda
 */
enum class IncomeCategory {
    SALARY,
    BONUS,
    DIVIDEND,
    RENT,
    GIFT,
    INTEREST,
    REFUND,
    SIDE_JOB,
    SALE,
    OTHER;
    
    fun getDisplayName(): String {
        return getDisplayName(Locale.getDefault())
    }
    
    fun getDisplayName(locale: Locale): String {
        return when (this) {
            SALARY -> when (locale.language) {
                "en" -> "Salary"
                "sr" -> if (locale.country == "RS") "Плата" else "Plata"
                else -> "Salary"
            }
            BONUS -> when (locale.language) {
                "en" -> "Bonus"
                "sr" -> if (locale.country == "RS") "Бонус" else "Bonus"
                else -> "Bonus"
            }
            DIVIDEND -> when (locale.language) {
                "en" -> "Dividend"
                "sr" -> if (locale.country == "RS") "Дивиденда" else "Dividenda"
                else -> "Dividend"
            }
            RENT -> when (locale.language) {
                "en" -> "Rent"
                "sr" -> if (locale.country == "RS") "Закуп" else "Zakup"
                else -> "Rent"
            }
            GIFT -> when (locale.language) {
                "en" -> "Gift"
                "sr" -> if (locale.country == "RS") "Поклон" else "Poklon"
                else -> "Gift"
            }
            INTEREST -> when (locale.language) {
                "en" -> "Interest"
                "sr" -> if (locale.country == "RS") "Камата" else "Kamata"
                else -> "Interest"
            }
            REFUND -> when (locale.language) {
                "en" -> "Refund"
                "sr" -> if (locale.country == "RS") "Повраћај новца" else "Povracaj novca"
                else -> "Refund"
            }
            SIDE_JOB -> when (locale.language) {
                "en" -> "Side Job"
                "sr" -> if (locale.country == "RS") "Додатни посао" else "Dodatni posao"
                else -> "Side Job"
            }
            SALE -> when (locale.language) {
                "en" -> "Sale"
                "sr" -> if (locale.country == "RS") "Продаја" else "Prodaja"
                else -> "Sale"
            }
            OTHER -> when (locale.language) {
                "en" -> "Other"
                "sr" -> if (locale.country == "RS") "Остало" else "Ostalo"
                else -> "Other"
            }
        }
    }
} 