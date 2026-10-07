package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.ShoppingList
import kotlinx.coroutines.flow.MutableStateFlow

class ListRepository private constructor() {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Чувамо листе у меморији за будућу употребу и реактивни UI
    // Тренутно се користи само за локално праћење промена
    private val _lists = MutableStateFlow<List<ShoppingList>>(emptyList())
    
    init {
        loadLists()
    }
    
    private fun loadLists() {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("users").document(userId)
            .collection("lists")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Грешка при учитавању листа", error)
                    return@addSnapshotListener
                }
                
                val listsList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                _lists.value = listsList
            }
    }

    companion object {
        private const val TAG = "ListRepository"
        
        @Volatile
        private var instance: ListRepository? = null
        
        fun getInstance(): ListRepository {
            return instance ?: synchronized(this) {
                instance ?: ListRepository().also { instance = it }
            }
        }
    }
} 