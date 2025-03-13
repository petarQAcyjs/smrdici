package com.petar.smrdici.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TransactionRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val transactionsCollection = firestore.collection("transactions")
    
    // Добијање тренутног корисника
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Додавање нове трансакције
    suspend fun addTransaction(transaction: Transaction): Result<Transaction> {
        return try {
            val transactionWithUser = transaction.copy(createdBy = currentUserId)
            val docRef = transactionsCollection.add(transactionWithUser).await()
            Result.success(transactionWithUser.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Ажурирање трансакције
    suspend fun updateTransaction(transaction: Transaction): Result<Transaction> {
        return try {
            transactionsCollection.document(transaction.id).set(transaction).await()
            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Брисање трансакције
    suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        return try {
            transactionsCollection.document(transactionId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Добијање свих трансакција за тренутног корисника
    fun getTransactionsForCurrentUser(): Flow<List<Transaction>> = callbackFlow {
        val listener = transactionsCollection
            .whereEqualTo("createdBy", currentUserId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaction::class.java)
                } ?: emptyList()
                
                trySend(transactions)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Добијање свих трансакција за породицу
    fun getTransactionsForFamily(familyId: String): Flow<List<Transaction>> = callbackFlow {
        val listener = transactionsCollection
            .whereEqualTo("familyId", familyId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaction::class.java)
                } ?: emptyList()
                
                trySend(transactions)
            }
        
        awaitClose { listener.remove() }
    }
} 