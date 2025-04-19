package com.petar.smrdici.data.repository

import com.petar.smrdici.data.model.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class IncomeRepository private constructor() {
    
    fun getAllIncomes(): Flow<List<Income>> {
        // Привремено враћамо празну листу
        return flowOf(emptyList())
    }
    
    fun addIncome() {
        // Имплементација додавања прихода
    }
    
    companion object {
        @Volatile
        private var instance: IncomeRepository? = null
        
        fun getInstance(): IncomeRepository {
            return instance ?: synchronized(this) {
                instance ?: IncomeRepository().also { instance = it }
            }
        }
    }
} 