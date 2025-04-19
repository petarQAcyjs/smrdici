package com.petar.smrdici.data.repository

import android.content.Context

/**
 * Centralno mesto za dobijanje instanci repozitorijuma
 * Koristi se za rešavanje problema sa privatnim konstruktorima
 */
object RepositoryManager {
    // Dummy instance klase ExpenseRepository za potrebe inicijalizacije
    private var expenseRepositoryInstance: ExpenseRepository? = null
    
    // Dummy instance klase IncomeRepository za potrebe inicijalizacije
    private var incomeRepositoryInstance: IncomeRepository? = null
    
    // Dummy instance klase AccountRepository za potrebe inicijalizacije
    private var accountRepositoryInstance: AccountRepository? = null
    
    /**
     * Vraća instancu ExpenseRepository za potrebe budžet funkcionalnosti
     * Koristi postojeću instancu koja ima sve konekcije pravilno uspostavljene
     */
    fun getExpenseRepositoryForBudget(): ExpenseRepository {
        return ExpenseRepository.getInstance()
    }
    
    /**
     * Vraća instancu IncomeRepository za potrebe budžet funkcionalnosti
     * Koristi postojeću instancu koja ima sve konekcije pravilno uspostavljene
     */
    fun getIncomeRepositoryForBudget(): IncomeRepository {
        return IncomeRepository.getInstance()
    }
    
    /**
     * Vraća instancu AccountRepository za potrebe budžet funkcionalnosti
     * Radi samo za čitanje podataka
     */
    fun getAccountRepositoryForBudget(context: Context): AccountRepository {
        if (accountRepositoryInstance == null) {
            // Koristimo regularnu factory metodu za kreiranje instance
            accountRepositoryInstance = AccountRepository.getInstance()
        }
        return accountRepositoryInstance!!
    }
    
    /**
     * Vraća instancu BudgetRepository za potrebe budžet funkcionalnosti
     */
    fun getBudgetRepositoryForBudget(): BudgetRepository {
        return BudgetRepository.getInstance()
    }
} 