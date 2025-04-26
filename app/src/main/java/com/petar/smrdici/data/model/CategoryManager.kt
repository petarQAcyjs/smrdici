package com.petar.smrdici.data.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/**
 * Menadžer za dinamičke kategorije koje mogu biti importovane
 * ali ne postoje kao deo Enum kategorija
 */
class CategoryManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Set za praćenje svih dodatnih kategorija troškova
    private val customExpenseCategories: MutableSet<String>
        get() = prefs.getStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, mutableSetOf()) ?: mutableSetOf()
    
    // Set za praćenje svih dodatnih kategorija prihoda
    private val customIncomeCategories: MutableSet<String>
        get() = prefs.getStringSet(KEY_CUSTOM_INCOME_CATEGORIES, mutableSetOf()) ?: mutableSetOf()
    
    /**
     * Dodaje novu kategoriju troškova
     */
    fun addExpenseCategory(categoryName: String) {
        // Prvo proveravamo da li kategorija već postoji u Enum-u
        try {
            ExpenseCategory.valueOf(categoryName)
            // Ako ne baci exception, znači da kategorija već postoji kao enum
            return
        } catch (_: IllegalArgumentException) {
            // Kategorija ne postoji u enum-u, dodajemo je u custom kategorije
            val updatedSet = customExpenseCategories.toMutableSet()
            updatedSet.add(categoryName)
            prefs.edit {
                putStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, updatedSet)
            }
            Log.d(TAG, "Dodata nova kategorija troškova: $categoryName")
        }
    }
    
    /**
     * Dodaje novu kategoriju prihoda
     */
    fun addIncomeCategory(categoryName: String) {
        // Prvo proveravamo da li kategorija već postoji u Enum-u
        try {
            IncomeCategory.valueOf(categoryName)
            // Ako ne baci exception, znači da kategorija već postoji kao enum
            return
        } catch (_: IllegalArgumentException) {
            // Kategorija ne postoji u enum-u, dodajemo je u custom kategorije
            val updatedSet = customIncomeCategories.toMutableSet()
            updatedSet.add(categoryName)
            prefs.edit {
                putStringSet(KEY_CUSTOM_INCOME_CATEGORIES, updatedSet)
            }
            Log.d(TAG, "Dodata nova kategorija prihoda: $categoryName")
        }
    }
    
    /**
     * Vraća ime prikazne kategorije za troškove - bilo iz enuma ili custom kategorija
     */
    fun getExpenseCategoryDisplayName(categoryName: String): String {
        return try {
            // Prvo pokušavamo dobiti iz enum-a
            val expenseCategory = ExpenseCategory.valueOf(categoryName)
            expenseCategory.getDisplayName()
        } catch (_: IllegalArgumentException) {
            // Ako nije u enumu, proveravamo da li je u custom kategorijama
            if (customExpenseCategories.contains(categoryName)) {
                categoryName
            } else {
                // Dodajemo je automatski ako nije ni u jednoj kategoriji
                addExpenseCategory(categoryName)
                categoryName
            }
        }
    }
    
    /**
     * Vraća ime prikazne kategorije za prihode - bilo iz enuma ili custom kategorija
     */
    fun getIncomeCategoryDisplayName(categoryName: String): String {
        return try {
            // Prvo pokušavamo dobiti iz enum-a
            val incomeCategory = IncomeCategory.valueOf(categoryName)
            incomeCategory.getDisplayName()
        } catch (_: IllegalArgumentException) {
            // Ako nije u enumu, proveravamo da li je u custom kategorijama
            if (customIncomeCategories.contains(categoryName)) {
                categoryName
            } else {
                // Dodajemo je automatski ako nije ni u jednoj kategoriji
                addIncomeCategory(categoryName)
                categoryName
            }
        }
    }
    
    /**
     * Vraća sve kategorije troškova - i enum i custom
     */
    fun getAllExpenseCategories(): List<String> {
        val enumCategories = ExpenseCategory.entries.map { it.name }
        return enumCategories + customExpenseCategories.toList()
    }
    
    /**
     * Vraća sve kategorije prihoda - i enum i custom
     */
    fun getAllIncomeCategories(): List<String> {
        val enumCategories = IncomeCategory.entries.map { it.name }
        return enumCategories + customIncomeCategories.toList()
    }
    
    /**
     * Praćenje da li kategorija već postoji kao custom ili enum
     */
    fun hasExpenseCategory(categoryName: String): Boolean {
        return try {
            ExpenseCategory.valueOf(categoryName)
            true
        } catch (_: IllegalArgumentException) {
            customExpenseCategories.contains(categoryName)
        }
    }
    
    /**
     * Praćenje da li kategorija već postoji kao custom ili enum
     */
    fun hasIncomeCategory(categoryName: String): Boolean {
        return try {
            IncomeCategory.valueOf(categoryName)
            true
        } catch (_: IllegalArgumentException) {
            customIncomeCategories.contains(categoryName)
        }
    }
    
    /**
     * Izmena postojeće kategorije troškova (samo za custom kategorije)
     */
    fun updateExpenseCategory(oldName: String, newName: String) {
        if (customExpenseCategories.contains(oldName)) {
            val updatedSet = customExpenseCategories.toMutableSet()
            updatedSet.remove(oldName)
            updatedSet.add(newName)
            prefs.edit {
                putStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, updatedSet)
            }
            Log.d(TAG, "Izmenjena kategorija troškova: $oldName -> $newName")
        }
    }

    /**
     * Izmena postojeće kategorije prihoda (samo za custom kategorije)
     */
    fun updateIncomeCategory(oldName: String, newName: String) {
        if (customIncomeCategories.contains(oldName)) {
            val updatedSet = customIncomeCategories.toMutableSet()
            updatedSet.remove(oldName)
            updatedSet.add(newName)
            prefs.edit {
                putStringSet(KEY_CUSTOM_INCOME_CATEGORIES, updatedSet)
            }
            Log.d(TAG, "Izmenjena kategorija prihoda: $oldName -> $newName")
        }
    }

    /**
     * Brisanje kategorije troškova (samo za custom kategorije)
     */
    fun deleteExpenseCategory(categoryName: String) {
        if (customExpenseCategories.contains(categoryName)) {
            val updatedSet = customExpenseCategories.toMutableSet()
            updatedSet.remove(categoryName)
            prefs.edit {
                putStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, updatedSet)
            }
            Log.d(TAG, "Obrisana kategorija troškova: $categoryName")
        }
    }

    /**
     * Brisanje kategorije prihoda (samo za custom kategorije)
     */
    fun deleteIncomeCategory(categoryName: String) {
        if (customIncomeCategories.contains(categoryName)) {
            val updatedSet = customIncomeCategories.toMutableSet()
            updatedSet.remove(categoryName)
            prefs.edit {
                putStringSet(KEY_CUSTOM_INCOME_CATEGORIES, updatedSet)
            }
            Log.d(TAG, "Obrisana kategorija prihoda: $categoryName")
        }
    }
    
    companion object {
        private const val TAG = "CategoryManager"
        private const val PREFS_NAME = "category_manager_prefs"
        private const val KEY_CUSTOM_EXPENSE_CATEGORIES = "custom_expense_categories"
        private const val KEY_CUSTOM_INCOME_CATEGORIES = "custom_income_categories"
        
        @Volatile
        private var instance: CategoryManager? = null
        
        fun getInstance(context: Context): CategoryManager {
            return instance ?: synchronized(this) {
                instance ?: CategoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
} 