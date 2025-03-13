package com.petar.smrdici.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.ShoppingItem
import com.petar.smrdici.data.model.ShoppingList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ShoppingListRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val listsCollection = firestore.collection("shopping_lists")
    
    // Добијање тренутног корисника
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Добијање свих листа за тренутног корисника
    fun getShoppingListsForCurrentUser(): Flow<List<ShoppingList>> = callbackFlow {
        val listener = listsCollection
            .whereEqualTo("createdBy", currentUserId)
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
    
    // Добијање једне листе по ID-у
    fun getShoppingList(listId: String): Flow<ShoppingList?> = callbackFlow {
        val listener = listsCollection
            .document(listId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val list = snapshot?.toObject(ShoppingList::class.java)
                trySend(list)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Додавање нове листе
    suspend fun addShoppingList(list: ShoppingList): Result<ShoppingList> {
        return try {
            val listWithUser = list.copy(createdBy = currentUserId)
            val docRef = listsCollection.add(listWithUser).await()
            Result.success(listWithUser.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Ажурирање листе
    suspend fun updateShoppingList(list: ShoppingList): Result<ShoppingList> {
        return try {
            listsCollection.document(list.id).set(list).await()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Брисање листе
    suspend fun deleteShoppingList(listId: String): Result<Unit> {
        return try {
            listsCollection.document(listId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Додавање ставке у листу
    suspend fun addItemToList(listId: String, item: ShoppingItem): Result<ShoppingList> {
        return try {
            val listDoc = listsCollection.document(listId).get().await()
            val list = listDoc.toObject(ShoppingList::class.java) ?: throw IllegalStateException("Листа не постоји")
            
            val newItem = item.copy(
                id = UUID.randomUUID().toString(),
                addedBy = currentUserId
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