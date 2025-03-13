package com.petar.smrdici.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Event
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class EventRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val eventsCollection = firestore.collection("events")
    
    // Добијање тренутног корисника
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Додавање новог догађаја
    suspend fun addEvent(event: Event): Result<Event> {
        return try {
            val eventWithUser = event.copy(createdBy = currentUserId)
            val docRef = eventsCollection.add(eventWithUser).await()
            Result.success(eventWithUser.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Ажурирање догађаја
    suspend fun updateEvent(event: Event): Result<Event> {
        return try {
            eventsCollection.document(event.id).set(event).await()
            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Брисање догађаја
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            eventsCollection.document(eventId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Добијање свих догађаја за тренутног корисника
    fun getEventsForCurrentUser(): Flow<List<Event>> = callbackFlow {
        val listener = eventsCollection
            .whereEqualTo("createdBy", currentUserId)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Добијање свих догађаја за породицу
    fun getEventsForFamily(familyId: String): Flow<List<Event>> = callbackFlow {
        val listener = eventsCollection
            .whereEqualTo("familyId", familyId)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Добијање догађаја за одређени временски период
    fun getEventsForPeriod(startDate: Date, endDate: Date): Flow<List<Event>> = callbackFlow {
        val startTimestamp = com.google.firebase.Timestamp(startDate)
        val endTimestamp = com.google.firebase.Timestamp(endDate)
        
        val listener = eventsCollection
            .whereGreaterThanOrEqualTo("startTime", startTimestamp)
            .whereLessThanOrEqualTo("startTime", endTimestamp)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
} 