package com.petar.smrdici.data.repository

/**
 * Centralno mesto za dobijanje instanci repozitorijuma
 * Koristi se za rešavanje problema sa privatnim konstruktorima
 */
object RepositoryManager {

    /**
     * Vraća instancu ExpenseRepository za potrebe finance funkcionalnosti
     * Koristi postojeću instancu koja ima sve konekcije pravilno uspostavljene
     */
    fun getExpenseRepositoryForFinance(): ExpenseRepository {
        return ExpenseRepository.getInstance()
    }

    /**
     * Vraća instancu IncomeRepository za potrebe finance funkcionalnosti
     * Koristi postojeću instancu koja ima sve konekcije pravilno uspostavljene
     */
    fun getIncomeRepositoryForFinance(): IncomeRepository {
        return IncomeRepository.getInstance()
    }

    /**
     * Vraća instancu AccountRepository za potrebe finance funkcionalnosti
     * Radi samo za čitanje podataka
     */
    fun getAccountRepositoryForFinance(): AccountRepository {
        return AccountRepository.getInstance()
    }
    
    /**
     * Legacy methods for backward compatibility
     */
    @Deprecated("Use getExpenseRepositoryForFinance() instead", ReplaceWith("getExpenseRepositoryForFinance()"))
    fun getExpenseRepositoryForBudget(): ExpenseRepository {
        return getExpenseRepositoryForFinance()
    }
    
    @Deprecated("Use getIncomeRepositoryForFinance() instead", ReplaceWith("getIncomeRepositoryForFinance()"))
    fun getIncomeRepositoryForBudget(): IncomeRepository {
        return getIncomeRepositoryForFinance()
    }
    
    @Deprecated("Use getAccountRepositoryForFinance() instead", ReplaceWith("getAccountRepositoryForFinance(context)"))
    fun getAccountRepositoryForBudget(): AccountRepository {
        return getAccountRepositoryForFinance()
    }
}
