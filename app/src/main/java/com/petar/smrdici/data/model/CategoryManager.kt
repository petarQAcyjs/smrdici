package com.petar.smrdici.data.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await
import com.petar.smrdici.data.model.ExpenseCategory
import com.petar.smrdici.data.model.IncomeCategory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
    
    // Firestore references
    private val firestore = FirebaseFirestore.getInstance()
    private val expenseCategoriesRef: CollectionReference = firestore.collection("expenseCategories")
    private val incomeCategoriesRef: CollectionReference = firestore.collection("incomeCategories")
    
    // Storage for category colors
    private fun getCategoryColorKey(categoryName: String, isExpense: Boolean): String {
        val prefix = if (isExpense) "expense_color_" else "income_color_"
        return prefix + categoryName
    }
    
    fun saveCategoryColor(categoryName: String, colorValue: Long, isExpense: Boolean) {
        val key = getCategoryColorKey(categoryName, isExpense)
        prefs.edit {
            putLong(key, colorValue)
        }
        Log.d(TAG, "Saved color for category: $categoryName, isExpense: $isExpense, color: $colorValue")
    }
    
    fun getCategoryColor(categoryName: String, isExpense: Boolean): Long? {
        val key = getCategoryColorKey(categoryName, isExpense)
        if (!prefs.contains(key)) {
            Log.d(TAG, "No saved color for category: $categoryName, isExpense: $isExpense")
            return null
        }
        
        val colorValue = prefs.getLong(key, 0)
        Log.d(TAG, "Retrieved color for category: $categoryName, isExpense: $isExpense, color: $colorValue")
        return colorValue
    }
    
    fun updateCategoryColor(oldName: String, newName: String, isExpense: Boolean) {
        val oldKey = getCategoryColorKey(oldName, isExpense)
        if (prefs.contains(oldKey)) {
            val colorValue = prefs.getLong(oldKey, 0)
            val newKey = getCategoryColorKey(newName, isExpense)
            prefs.edit {
                putLong(newKey, colorValue)
                remove(oldKey)
            }
            Log.d(TAG, "Updated color key from $oldName to $newName")
        }
    }
    
    // Storage for category icons
    private fun getCategoryIconKey(categoryName: String, isExpense: Boolean): String {
        val prefix = if (isExpense) "expense_icon_" else "income_icon_"
        return prefix + categoryName
    }
    
    fun saveCategoryIcon(categoryName: String, iconName: String, isExpense: Boolean) {
        // Check if the icon name is an ImageVector reference
        val cleanIconName = if (iconName.startsWith("ImageVector@")) {
            // Use a default name instead
            if (isExpense) "ShoppingCart" else "AttachMoney"
        } else {
            iconName
        }
        
        val key = getCategoryIconKey(categoryName, isExpense)
        prefs.edit {
            putString(key, cleanIconName)
        }
        Log.d(TAG, "Saved icon for category: $categoryName, isExpense: $isExpense, icon: $cleanIconName")
    }
    
    fun getCategoryIcon(categoryName: String, isExpense: Boolean): String? {
        val key = getCategoryIconKey(categoryName, isExpense)
        if (!prefs.contains(key)) {
            Log.d(TAG, "No saved icon for category: $categoryName, isExpense: $isExpense")
            return null
        }
        
        val iconName = prefs.getString(key, null)
        Log.d(TAG, "Retrieved icon for category: $categoryName, isExpense: $isExpense, icon: $iconName")
        return iconName
    }
    
    fun updateCategoryIcon(oldName: String, newName: String, isExpense: Boolean) {
        val oldKey = getCategoryIconKey(oldName, isExpense)
        if (prefs.contains(oldKey)) {
            val iconName = prefs.getString(oldKey, null)
            if (iconName != null) {
                val newKey = getCategoryIconKey(newName, isExpense)
                prefs.edit {
                    putString(newKey, iconName)
                    remove(oldKey)
                }
                Log.d(TAG, "Updated icon key from $oldName to $newName")
            }
        }
    }
    
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
     * Izmena postojeće kategorije troškova (sada za sve kategorije)
     */
    fun updateExpenseCategory(oldName: String, newName: String) {
        val updatedSet = customExpenseCategories.toMutableSet()
        updatedSet.remove(oldName)
        updatedSet.add(newName)
        prefs.edit {
            putStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, updatedSet)
        }
        Log.d(TAG, "Izmenjena kategorija troškova: $oldName -> $newName")
    }

    /**
     * Izmena postojeće kategorije prihoda (sada za sve kategorije)
     */
    fun updateIncomeCategory(oldName: String, newName: String) {
        val updatedSet = customIncomeCategories.toMutableSet()
        updatedSet.remove(oldName)
        updatedSet.add(newName)
        prefs.edit {
            putStringSet(KEY_CUSTOM_INCOME_CATEGORIES, updatedSet)
        }
        Log.d(TAG, "Izmenjena kategorija prihoda: $oldName -> $newName")
    }

    /**
     * Brisanje kategorije troškova (sada za sve kategorije)
     */
    fun deleteExpenseCategory(categoryName: String) {
        val updatedSet = customExpenseCategories.toMutableSet()
        updatedSet.remove(categoryName)
        prefs.edit {
            putStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, updatedSet)
        }
        Log.d(TAG, "Obrisana kategorija troškova: $categoryName")
    }

    /**
     * Brisanje kategorije prihoda (sada za sve kategorije)
     */
    fun deleteIncomeCategory(categoryName: String) {
        val updatedSet = customIncomeCategories.toMutableSet()
        updatedSet.remove(categoryName)
        prefs.edit {
            putStringSet(KEY_CUSTOM_INCOME_CATEGORIES, updatedSet)
        }
        Log.d(TAG, "Obrisana категорија прихода: $categoryName")
    }
    
    /**
     * Vraća sve kategorije troškova - sada samo custom
     */
    fun getAllExpenseCategories(): List<String> {
        return customExpenseCategories.toList()
    }
    
    /**
     * Vraća sve kategorije prihoda - sada samo custom
     */
    fun getAllIncomeCategories(): List<String> {
        return customIncomeCategories.toList()
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
    
    // Firestore: Fetch all expense categories
    suspend fun fetchAllExpenseCategoriesFromFirestore(): List<String> {
        val snapshot = expenseCategoriesRef.get().await()
        return snapshot.documents.mapNotNull { it.toObject(FirestoreCategory::class.java)?.name }
    }

    // Firestore: Fetch all income categories
    suspend fun fetchAllIncomeCategoriesFromFirestore(): List<String> {
        val snapshot = incomeCategoriesRef.get().await()
        return snapshot.documents.mapNotNull { it.toObject(FirestoreCategory::class.java)?.name }
    }

    // Firestore: Add expense category
    suspend fun addExpenseCategoryToFirestore(categoryName: String) {
        expenseCategoriesRef.document(categoryName).set(FirestoreCategory(name = categoryName)).await()
    }

    // Firestore: Add income category
    suspend fun addIncomeCategoryToFirestore(categoryName: String) {
        incomeCategoriesRef.document(categoryName).set(FirestoreCategory(name = categoryName)).await()
    }

    // Firestore: Update expense category
    suspend fun updateExpenseCategoryInFirestore(oldName: String, newName: String) {
        // Delete old, add new
        expenseCategoriesRef.document(oldName).delete().await()
        expenseCategoriesRef.document(newName).set(FirestoreCategory(name = newName)).await()
    }

    // Firestore: Update income category
    suspend fun updateIncomeCategoryInFirestore(oldName: String, newName: String) {
        incomeCategoriesRef.document(oldName).delete().await()
        incomeCategoriesRef.document(newName).set(FirestoreCategory(name = newName)).await()
    }

    // Firestore: Delete expense category
    suspend fun deleteExpenseCategoryFromFirestore(categoryName: String) {
        expenseCategoriesRef.document(categoryName).delete().await()
    }

    // Firestore: Delete income category
    suspend fun deleteIncomeCategoryFromFirestore(categoryName: String) {
        incomeCategoriesRef.document(categoryName).delete().await()
    }
    
    // Firestore: Migrate all enum categories to Firestore
    suspend fun migrateEnumCategoriesToFirestore() {
        // Expense categories
        for (category in ExpenseCategory.entries) {
            val doc = expenseCategoriesRef.document(category.name).get().await()
            if (!doc.exists()) {
                expenseCategoriesRef.document(category.name).set(FirestoreCategory(name = category.name)).await()
            }
        }
        // Income categories
        for (category in IncomeCategory.entries) {
            val doc = incomeCategoriesRef.document(category.name).get().await()
            if (!doc.exists()) {
                incomeCategoriesRef.document(category.name).set(FirestoreCategory(name = category.name)).await()
            }
        }
    }
    
    // Save categories to local storage
    fun saveExpenseCategoriesToLocal(categories: List<String>) {
        prefs.edit {
            putStringSet(KEY_CUSTOM_EXPENSE_CATEGORIES, categories.toSet())
        }
    }
    fun saveIncomeCategoriesToLocal(categories: List<String>) {
        prefs.edit {
            putStringSet(KEY_CUSTOM_INCOME_CATEGORIES, categories.toSet())
        }
    }

    // Fetch categories with offline fallback
    suspend fun getExpenseCategoriesWithFallback(): List<String> {
        return try {
            val firestoreCategories = fetchAllExpenseCategoriesFromFirestore()
            saveExpenseCategoriesToLocal(firestoreCategories)
            firestoreCategories
        } catch (e: Exception) {
            Log.d(TAG, "Firestore unavailable, loading expense categories from local cache.")
            getAllExpenseCategories()
        }
    }
    suspend fun getIncomeCategoriesWithFallback(): List<String> {
        return try {
            val firestoreCategories = fetchAllIncomeCategoriesFromFirestore()
            saveIncomeCategoriesToLocal(firestoreCategories)
            firestoreCategories
        } catch (e: Exception) {
            Log.d(TAG, "Firestore unavailable, loading income categories from local cache.")
            getAllIncomeCategories()
        }
    }

    // Add category (updates both Firestore and local)
    suspend fun addExpenseCategoryBoth(categoryName: String) {
        addExpenseCategoryToFirestore(categoryName)
        val updated = getExpenseCategoriesWithFallback()
        saveExpenseCategoriesToLocal(updated)
    }
    suspend fun addIncomeCategoryBoth(categoryName: String) {
        addIncomeCategoryToFirestore(categoryName)
        val updated = getIncomeCategoriesWithFallback()
        saveIncomeCategoriesToLocal(updated)
    }
    // Edit category (updates both Firestore and local)
    suspend fun updateExpenseCategoryBoth(oldName: String, newName: String) {
        updateExpenseCategoryInFirestore(oldName, newName)
        val updated = getExpenseCategoriesWithFallback()
        saveExpenseCategoriesToLocal(updated)
    }
    suspend fun updateIncomeCategoryBoth(oldName: String, newName: String) {
        updateIncomeCategoryInFirestore(oldName, newName)
        val updated = getIncomeCategoriesWithFallback()
        saveIncomeCategoriesToLocal(updated)
    }
    // Delete category (updates both Firestore and local)
    suspend fun deleteExpenseCategoryBoth(categoryName: String) {
        deleteExpenseCategoryFromFirestore(categoryName)
        val updated = getExpenseCategoriesWithFallback()
        saveExpenseCategoriesToLocal(updated)
    }
    suspend fun deleteIncomeCategoryBoth(categoryName: String) {
        deleteIncomeCategoryFromFirestore(categoryName)
        val updated = getIncomeCategoriesWithFallback()
        saveIncomeCategoriesToLocal(updated)
    }

    // (Optional) Real-time sync: add a snapshot listener for Firestore categories
    // fun listenToExpenseCategoriesRealtime(onUpdate: (List<String>) -> Unit) { /* ... */ }
    // fun listenToIncomeCategoriesRealtime(onUpdate: (List<String>) -> Unit) { /* ... */ }
    
    // Push all enum categories to Firestore if not present
    suspend fun ensureEnumCategoriesInFirestore() {
        // Expense categories
        for (category in ExpenseCategory.entries) {
            val doc = expenseCategoriesRef.document(category.name).get().await()
            if (!doc.exists()) {
                expenseCategoriesRef.document(category.name).set(FirestoreCategory(name = category.name)).await()
            }
        }
        // Income categories
        for (category in IncomeCategory.entries) {
            val doc = incomeCategoriesRef.document(category.name).get().await()
            if (!doc.exists()) {
                incomeCategoriesRef.document(category.name).set(FirestoreCategory(name = category.name)).await()
            }
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
                instance ?: CategoryManager(context.applicationContext).also { 
                    instance = it
                    it.cleanupInvalidIconReferences()
                }
            }
        }
    }
    
    /**
     * Cleanup any invalid icon references that might be stored
     */
    private fun cleanupInvalidIconReferences() {
        val allKeys = prefs.all.keys
        val iconKeys = allKeys.filter { it.startsWith("expense_icon_") || it.startsWith("income_icon_") }
        
        for (key in iconKeys) {
            val iconName = prefs.getString(key, null)
            if (iconName?.startsWith("ImageVector@") == true) {
                val isExpense = key.startsWith("expense_icon_")
                val categoryName = if (isExpense) {
                    key.removePrefix("expense_icon_")
                } else {
                    key.removePrefix("income_icon_")
                }
                
                // Replace with default icon name
                val defaultName = if (isExpense) "ShoppingCart" else "AttachMoney"
                prefs.edit {
                    putString(key, defaultName)
                }
                Log.d(TAG, "Fixed invalid icon reference for category: $categoryName, isExpense: $isExpense, from: $iconName to: $defaultName")
            }
        }
    }
}

data class FirestoreCategory(
    val name: String = ""
) 