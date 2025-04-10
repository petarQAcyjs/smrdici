package com.petar.smrdici.data.model

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
        return when (this) {
            SALARY -> "Плата"
            BONUS -> "Бонус"
            DIVIDEND -> "Дивиденда"
            RENT -> "Закуп"
            GIFT -> "Поклон"
            INTEREST -> "Камата"
            REFUND -> "Повраћај новца"
            SIDE_JOB -> "Додатни посао"
            SALE -> "Продаја"
            OTHER -> "Остало"
        }
    }
} 