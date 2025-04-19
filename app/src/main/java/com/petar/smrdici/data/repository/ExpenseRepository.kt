package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

class ExpenseRepository private constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    
    // Додајемо методу за добијање трошкова за одређени период
    fun getExpensesForPeriod(startDate: Date, endDate: Date): Flow<List<Expense>> {
        Log.d("ExpenseRepository", "Учитавам трошкове за период од ${startDate} до ${endDate}")
        
        // Овде би требало да имплементирате филтрирање по датуму
        // За сада ћемо само вратити све трошкове
        return getAllExpenses()
    }
    
    fun getAllExpenses(): Flow<List<Expense>> {
        // Привремено враћамо празну листу
        return _expenses.asStateFlow()
    }
    
    fun addExpense() {
        // Имплементација додавања расхода
    }
    
    companion object {
        @Volatile
        private var instance: ExpenseRepository? = null
        
        fun getInstance(): ExpenseRepository {
            return instance ?: synchronized(this) {
                instance ?: ExpenseRepository().also { instance = it }
            }
        }
    }
} 