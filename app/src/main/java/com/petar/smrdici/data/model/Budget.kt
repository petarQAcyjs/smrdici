package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Model koji predstavlja budžet sa informacijama o dozvoljenoj potrošnji 
 * u određenom vremenskom periodu i kategorijama.
 */
data class Budget(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    
    // Lista kategorija na koje se budžet odnosi (prazna lista znači sve kategorije)
    val categoryIds: List<String> = emptyList(),
    
    // ID računa na koji se budžet odnosi (prazan string znači svi računi)
    val accountId: String = "",
    
    // Vremenski period za koji važi budžet
    val startDate: Timestamp = Timestamp(Date()),
    val endDate: Timestamp = Timestamp(Date()),
    
    // Poredak za sortiranje
    val orderId: Double = 0.0,
    
    // Status budžeta
    val isActive: Boolean = true,
    
    // Tip budžeta (rashodi/prihodi)
    val type: BudgetType = BudgetType.EXPENSE
) {
    // Pomoćne metode za konverziju startDate i endDate u Date objekte
    fun getStartDateObject(): Date? {
        return startDate.getDateObject()
    }
    
    fun getEndDateObject(): Date? {
        return endDate.getDateObject()
    }
}

// Extension funkcije za Timestamp klasu
fun Timestamp.getDateObject(): Date? {
    return try {
        this.toDate()
    } catch (_: Exception) {
        null
    }
}

/**
 * Tip budžeta - za rashode ili prihode
 */
enum class BudgetType {
    EXPENSE, INCOME
}

/**
 * Model koji prikazuje budžet sa dodatnim informacijama o potrošnji
 */
data class DisplayBudget(
    val budget: Budget,
    val spentAmount: Double = 0.0,
    
    // Izvedena polja za lakši prikaz
    val remainingAmount: Double = budget.amount - spentAmount,
    val percentSpent: Double = if (budget.amount > 0) spentAmount / budget.amount else 0.0
) {
    // Vraća indikator statusa budžeta za vizuelni prikaz
    @Suppress("unused")
    fun getBudgetStatus(): BudgetStatus {
        return when {
            percentSpent <= 0.25 -> BudgetStatus.EXCELLENT
            percentSpent <= 0.50 -> BudgetStatus.GOOD
            percentSpent <= 0.75 -> BudgetStatus.WARNING
            percentSpent <= 1.0 -> BudgetStatus.DANGER
            else -> BudgetStatus.EXCEEDED
        }
    }
}

/**
 * Status budžeta za vizuelni prikaz
 */
enum class BudgetStatus {
    EXCELLENT, GOOD, WARNING, DANGER, EXCEEDED
} 