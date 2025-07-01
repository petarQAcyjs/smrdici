package com.petar.smrdici.data.model

import java.util.Locale
import androidx.compose.ui.graphics.Color

enum class ExpenseCategory {
    GROCERIES,
    UTILITIES,
    RENT,
    TRANSPORTATION,
    ENTERTAINMENT,
    HEALTH,
    EDUCATION,
    CLOTHING,
    TRAVEL,
    FOOD,
    COFFEE,
    ALCOHOL,
    CIGARETTES,
    GIFTS,
    SUBSCRIPTIONS,
    ELECTRONICS,
    HOME,
    BEAUTY,
    PETS,
    SPORTS,
    INVESTMENTS,
    DEBT,
    INSURANCE,
    TAXES,
    CHARITY,
    BUSINESS,
    CHILDREN,
    PERSONAL_CARE,
    SHOPPING,
    MAINTENANCE,
    SERVICES,
    SAVINGS,
    LOAN,
    RAMPA,
    PARKING,
    OTHER;
    
    fun getDisplayName(): String {
        return getDisplayName(Locale.getDefault())
    }
    
    fun getDisplayName(locale: Locale): String {
        return when (this) {
            GROCERIES -> when (locale.language) {
                "en" -> "Groceries"
                "sr" -> if (locale.country == "RS") "Намирнице" else "Namirnice"
                else -> "Groceries"
            }
            UTILITIES -> when (locale.language) {
                "en" -> "Utilities"
                "sr" -> if (locale.country == "RS") "Рачуни" else "Racuni"
                else -> "Utilities"
            }
            RENT -> when (locale.language) {
                "en" -> "Rent"
                "sr" -> if (locale.country == "RS") "Станарина" else "Stanarina"
                else -> "Rent"
            }
            TRANSPORTATION -> when (locale.language) {
                "en" -> "Transportation"
                "sr" -> if (locale.country == "RS") "Превоз" else "Prevoz"
                else -> "Transportation"
            }
            ENTERTAINMENT -> when (locale.language) {
                "en" -> "Entertainment"
                "sr" -> if (locale.country == "RS") "Забава" else "Zabava"
                else -> "Entertainment"
            }
            HEALTH -> when (locale.language) {
                "en" -> "Health"
                "sr" -> if (locale.country == "RS") "Здравље" else "Zdravlje"
                else -> "Health"
            }
            EDUCATION -> when (locale.language) {
                "en" -> "Education"
                "sr" -> if (locale.country == "RS") "Образовање" else "Obrazovanje"
                else -> "Education"
            }
            CLOTHING -> when (locale.language) {
                "en" -> "Clothing"
                "sr" -> if (locale.country == "RS") "Одећа" else "Odeca"
                else -> "Clothing"
            }
            TRAVEL -> when (locale.language) {
                "en" -> "Travel"
                "sr" -> if (locale.country == "RS") "Путовање" else "Putovanje"
                else -> "Travel"
            }
            FOOD -> when (locale.language) {
                "en" -> "Food"
                "sr" -> if (locale.country == "RS") "Храна" else "Hrana"
                else -> "Food"
            }
            COFFEE -> when (locale.language) {
                "en" -> "Coffee"
                "sr" -> if (locale.country == "RS") "Кафа" else "Kafa"
                else -> "Coffee"
            }
            ALCOHOL -> when (locale.language) {
                "en" -> "Alcohol"
                "sr" -> if (locale.country == "RS") "Алкохол" else "Alkohol"
                else -> "Alcohol"
            }
            CIGARETTES -> when (locale.language) {
                "en" -> "Cigarettes"
                "sr" -> if (locale.country == "RS") "Цигарете" else "Cigarete"
                else -> "Cigarettes"
            }
            GIFTS -> when (locale.language) {
                "en" -> "Gifts"
                "sr" -> if (locale.country == "RS") "Поклони" else "Pokloni"
                else -> "Gifts"
            }
            SUBSCRIPTIONS -> when (locale.language) {
                "en" -> "Subscriptions"
                "sr" -> if (locale.country == "RS") "Претплате" else "Pretplate"
                else -> "Subscriptions"
            }
            ELECTRONICS -> when (locale.language) {
                "en" -> "Electronics"
                "sr" -> if (locale.country == "RS") "Електроника" else "Elektronika"
                else -> "Electronics"
            }
            HOME -> when (locale.language) {
                "en" -> "Home"
                "sr" -> if (locale.country == "RS") "Кућа" else "Kuca"
                else -> "Home"
            }
            BEAUTY -> when (locale.language) {
                "en" -> "Beauty"
                "sr" -> if (locale.country == "RS") "Лепота" else "Lepota"
                else -> "Beauty"
            }
            PETS -> when (locale.language) {
                "en" -> "Pets"
                "sr" -> if (locale.country == "RS") "Кућни љубимци" else "Kucni ljubimci"
                else -> "Pets"
            }
            SPORTS -> when (locale.language) {
                "en" -> "Sports"
                "sr" -> if (locale.country == "RS") "Спорт" else "Sport"
                else -> "Sports"
            }
            INVESTMENTS -> when (locale.language) {
                "en" -> "Investments"
                "sr" -> if (locale.country == "RS") "Инвестиције" else "Investicije"
                else -> "Investments"
            }
            DEBT -> when (locale.language) {
                "en" -> "Debt"
                "sr" -> if (locale.country == "RS") "Дуг" else "Dug"
                else -> "Debt"
            }
            INSURANCE -> when (locale.language) {
                "en" -> "Insurance"
                "sr" -> if (locale.country == "RS") "Осигурање" else "Osiguranje"
                else -> "Insurance"
            }
            TAXES -> when (locale.language) {
                "en" -> "Taxes"
                "sr" -> if (locale.country == "RS") "Порези" else "Porezi"
                else -> "Taxes"
            }
            CHARITY -> when (locale.language) {
                "en" -> "Charity"
                "sr" -> if (locale.country == "RS") "Донације" else "Donacije"
                else -> "Charity"
            }
            BUSINESS -> when (locale.language) {
                "en" -> "Business"
                "sr" -> if (locale.country == "RS") "Пословни трошкови" else "Poslovni troskovi"
                else -> "Business"
            }
            CHILDREN -> when (locale.language) {
                "en" -> "Children"
                "sr" -> if (locale.country == "RS") "Деца" else "Deca"
                else -> "Children"
            }
            PERSONAL_CARE -> when (locale.language) {
                "en" -> "Personal Care"
                "sr" -> if (locale.country == "RS") "Лична нега" else "Licna nega"
                else -> "Personal Care"
            }
            SHOPPING -> when (locale.language) {
                "en" -> "Shopping"
                "sr" -> if (locale.country == "RS") "Куповина" else "Kupovina"
                else -> "Shopping"
            }
            MAINTENANCE -> when (locale.language) {
                "en" -> "Maintenance"
                "sr" -> if (locale.country == "RS") "Одржавање" else "Odrzavanje"
                else -> "Maintenance"
            }
            SERVICES -> when (locale.language) {
                "en" -> "Services"
                "sr" -> if (locale.country == "RS") "Услуге" else "Usluge"
                else -> "Services"
            }
            SAVINGS -> when (locale.language) {
                "en" -> "Savings"
                "sr" -> if (locale.country == "RS") "Штедња" else "Stednja"
                else -> "Savings"
            }
            LOAN -> when (locale.language) {
                "en" -> "Loan"
                "sr" -> if (locale.country == "RS") "Кредит" else "Kredit"
                else -> "Loan"
            }
            RAMPA -> when (locale.language) {
                "en" -> "Toll"
                "sr" -> if (locale.country == "RS") "Рампа" else "Rampa"
                else -> "Toll"
            }
            PARKING -> when (locale.language) {
                "en" -> "Parking"
                "sr" -> if (locale.country == "RS") "Паркинг" else "Parking"
                else -> "Parking"
            }
            OTHER -> when (locale.language) {
                "en" -> "Other"
                "sr" -> if (locale.country == "RS") "Остало" else "Ostalo"
                else -> "Other"
            }
        }
    }
}

// Function to get color for a category
fun getExpenseCategoryColor(category: String): Color {
    return try {
        val enumCategory = ExpenseCategory.valueOf(category)
        when (enumCategory) {
            ExpenseCategory.FOOD -> Color(0xFFE57373) // Red
            ExpenseCategory.TRANSPORTATION -> Color(0xFF64B5F6) // Blue
            ExpenseCategory.ENTERTAINMENT -> Color(0xFFFFD54F) // Yellow
            ExpenseCategory.UTILITIES -> Color(0xFF81C784) // Green
            ExpenseCategory.RENT -> Color(0xFFBA68C8) // Purple
            ExpenseCategory.SHOPPING -> Color(0xFF4FC3F7) // Light Blue
            ExpenseCategory.HEALTH -> Color(0xFFFF8A65) // Orange
            ExpenseCategory.EDUCATION -> Color(0xFF9575CD) // Deep Purple
            ExpenseCategory.TRAVEL -> Color(0xFF4DB6AC) // Teal
            ExpenseCategory.OTHER -> Color(0xFFF06292) // Pink
            else -> Color(0xFFF06292) // Pink for any other categories
        }
    } catch (e: IllegalArgumentException) {
        // For custom categories, use a hash-based color
        val index = Math.abs(category.hashCode()) % 10
        val colors = listOf(
            Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFFFFD54F),
            Color(0xFF81C784), Color(0xFFBA68C8), Color(0xFF4FC3F7),
            Color(0xFFFF8A65), Color(0xFF9575CD), Color(0xFF4DB6AC),
            Color(0xFFF06292)
        )
        colors[index]
    }
} 