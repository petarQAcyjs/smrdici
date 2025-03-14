package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class ShoppingListRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val listsCollection = firestore.collection("shopping_lists")
    private val currentUserId = auth.currentUser?.uid ?: ""
    
    // Учитавање свих листа за куповину корисника
    suspend fun getShoppingLists(): List<ShoppingList> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            
            val snapshot = firestore.collection("shopping_lists")
                .whereEqualTo("createdBy", userId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    val list = doc.toObject(ShoppingList::class.java)
                    list?.id = doc.id
                    list
                } catch (e: Exception) {
                    Log.e("ShoppingListRepository", "Грешка при обради листе", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при учитавању листа", e)
            emptyList()
        }
    }
    
    // Учитавање једне листе за куповину
    suspend fun getShoppingList(listId: String): ShoppingList? {
        return try {
            val doc = firestore.collection("shopping_lists")
                .document(listId)
                .get()
                .await()
            
            val list = doc.toObject(ShoppingList::class.java)
            list?.id = doc.id
            list
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при учитавању листе", e)
            null
        }
    }
    
    // Додавање нове листе за куповину
    suspend fun addShoppingList(title: String): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            
            val newList = ShoppingList(
                title = title,
                createdBy = userId,
                createdAt = Timestamp.now(),
                isCompleted = false,
                items = emptyList()
            )
            
            firestore.collection("shopping_lists")
                .add(newList)
                .await()
            
            true
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при додавању листе", e)
            false
        }
    }
    
    // Ажурирање листе за куповину
    suspend fun updateShoppingList(list: ShoppingList): Boolean {
        return try {
            firestore.collection("shopping_lists").document(list.id ?: "")
                .set(list)
                .await()
            
            true
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при ажурирању листе", e)
            false
        }
    }
    
    // Брисање листе за куповину
    suspend fun deleteShoppingList(listId: String): Boolean {
        return try {
            firestore.collection("shopping_lists").document(listId)
                .delete()
                .await()
            
            true
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при брисању листе", e)
            false
        }
    }
    
    // Додавање ставке у листу за куповину
    suspend fun addItemToList(listId: String, name: String, quantity: Int): Boolean {
        return try {
            val list = getShoppingList(listId) ?: return false
            
            val newItem = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = name,
                quantity = quantity,
                isCompleted = false,
                note = ""
            )
            
            val updatedItems = list.items + newItem
            val updatedList = list.copy(items = updatedItems)
            
            updateShoppingList(updatedList)
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при додавању ставке", e)
            false
        }
    }
    
    // Ажурирање статуса ставке (завршено/незавршено)
    suspend fun toggleItemStatus(listId: String, itemId: String): Boolean {
        return try {
            val list = getShoppingList(listId) ?: return false
            
            val updatedItems = list.items.map { item ->
                if (item.id == itemId) {
                    item.copy(isCompleted = !item.isCompleted)
                } else {
                    item
                }
            }
            
            val updatedList = list.copy(items = updatedItems)
            
            updateShoppingList(updatedList)
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при ажурирању статуса ставке", e)
            false
        }
    }
    
    // Брисање ставке из листе
    suspend fun deleteItemFromList(listId: String, itemId: String): Boolean {
        return try {
            val list = getShoppingList(listId) ?: return false
            
            val updatedItems = list.items.filter { it.id != itemId }
            val updatedList = list.copy(items = updatedItems)
            
            updateShoppingList(updatedList)
        } catch (e: Exception) {
            Log.e("ShoppingListRepository", "Грешка при брисању ставке", e)
            false
        }
    }
    
    // Добијање свих листа за породицу
    fun getShoppingListsForFamily(familyId: String): Flow<List<ShoppingList>> = callbackFlow {
        val listener = listsCollection
            .whereEqualTo("familyId", familyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val lists = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ShoppingList::class.java)
                } ?: emptyList()
                
                trySend(lists)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Додавање ставке у листу
    suspend fun addItemToList(listId: String, item: ShoppingItem): Result<ShoppingList> {
        return try {
            val listDoc = listsCollection.document(listId).get().await()
            val list = listDoc.toObject(ShoppingList::class.java) ?: throw IllegalStateException("Листа не постоји")
            
            val newItem = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = item.name,
                quantity = item.quantity,
                isCompleted = false,
                note = ""
            )
            
            val updatedList = list.copy(items = list.items + newItem)
            listsCollection.document(listId).set(updatedList).await()
            
            Result.success(updatedList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Ажурирање ставке у листи
    suspend fun updateItemInList(listId: String, item: ShoppingItem): Result<ShoppingList> {
        return try {
            val listDoc = listsCollection.document(listId).get().await()
            val list = listDoc.toObject(ShoppingList::class.java) ?: throw IllegalStateException("Листа не постоји")
            
            val updatedItems = list.items.map { 
                if (it.id == item.id) item else it 
            }
            
            val updatedList = list.copy(items = updatedItems)
            listsCollection.document(listId).set(updatedList).await()
            
            Result.success(updatedList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Брисање ставке из листе
    suspend fun removeItemFromList(listId: String, itemId: String): Result<ShoppingList> {
        return try {
            val listDoc = listsCollection.document(listId).get().await()
            val list = listDoc.toObject(ShoppingList::class.java) ?: throw IllegalStateException("Листа не постоји")
            
            val updatedItems = list.items.filter { it.id != itemId }
            val updatedList = list.copy(items = updatedItems)
            
            listsCollection.document(listId).set(updatedList).await()
            
            Result.success(updatedList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 