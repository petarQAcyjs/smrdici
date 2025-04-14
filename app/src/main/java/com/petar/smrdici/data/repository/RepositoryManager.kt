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
     * Radi samo za čitanje podataka jer nema AccountRepository instancu
     */
    fun getExpenseRepositoryForBudget(): ExpenseRepository {
        if (expenseRepositoryInstance == null) {
            // Refleksija za pristup privatnom konstruktoru
            val constructor = ExpenseRepository::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            expenseRepositoryInstance = constructor.newInstance()
        }
        return expenseRepositoryInstance!!
    }
    
    /**
     * Vraća instancu IncomeRepository za potrebe budžet funkcionalnosti
     * Radi samo za čitanje podataka jer nema AccountRepository instancu
     */
    fun getIncomeRepositoryForBudget(): IncomeRepository {
        if (incomeRepositoryInstance == null) {
            // Refleksija za pristup privatnom konstruktoru
            val constructor = IncomeRepository::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            incomeRepositoryInstance = constructor.newInstance()
        }
        return incomeRepositoryInstance!!
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
} 