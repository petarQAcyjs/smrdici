package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.data.model.ShoppingList
import kotlinx.coroutines.tasks.await

class ListsRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun syncLists(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
            val familyId = "default" // Подразумевана породица за дељење

            Log.d("ListsRepository", "Почињем синхронизацију листа")

            // Узимамо све листе из базе
            val userListsSnapshot = firestore.collection("shopping_lists")
                .whereEqualTo("createdBy", userId)
                .get()
                .await()

            val familyListsSnapshot = firestore.collection("shopping_lists")
                .whereEqualTo("familyId", familyId)
                .get()
                .await()

            // Комбинујемо све листе
            val allDocs = userListsSnapshot.documents + familyListsSnapshot.documents
            val lists = allDocs.mapNotNull { doc ->
                try {
                    doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e("ListsRepository", "Грешка при конверзији документа", e)
                    null
                }
            }

            Log.d("ListsRepository", "Синхронизовано ${lists.size} листа")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ListsRepository", "Грешка при синхронизацији", e)
            Result.failure(e)
        }
    }
} 