package com.petar.smrdici.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class EventRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val eventsCollection = firestore.collection("events")
    
    // Добијање тренутног корисника
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Функција за синхронизацију догађаја
    suspend fun syncEvents(): Boolean {
        try {
            val userId = auth.currentUser?.uid ?: return false
            
            // Овде би требало да имплементирате логику за синхронизацију између
            // Firebase-а и вашег интерног календара у апликацији
            
            // На пример, можете да учитате све догађаје из Firebase-а
            val firebaseEvents = getEventsFromFirebase()
            
            // И затим их ажурирате у вашем локалном складишту или стању апликације
            
            return true
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при синхронизацији догађаја", e)
            return false
        }
    }
    
    // Функција за добијање догађаја из Firebase-а
    private suspend fun getEventsFromFirebase(): List<Event> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("events")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    val event = doc.toObject(Event::class.java)
                    event?.id = doc.id
                    event
                } catch (e: Exception) {
                    Log.e("EventRepository", "Грешка при обради догађаја", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при учитавању из Firestore-а", e)
            emptyList()
        }
    }
    
    // Функција за праћење догађаја у реалном времену
    fun getEventsFlow() = callbackFlow {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            trySend(emptyList<Event>())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("users")
            .document(userId)
            .collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("EventRepository", "Грешка при праћењу догађаја", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    val event = doc.toObject(Event::class.java)
                    event?.id = doc.id
                    event
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Функција за добијање свих догађаја из локалног календара
    suspend fun getEventsFromCalendar(): List<Event> {
        return withContext(Dispatchers.IO) {
            val events = mutableListOf<Event>()
            val contentResolver: ContentResolver = context.contentResolver
            
            // Пројекција за упит
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.EVENT_COLOR
            )
            
            // Упит за догађаје
            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))
                    val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                    val description = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION))
                    val startMillis = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    val endMillis = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                    val calendarId = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                    
                    val startDate = Date(startMillis)
                    val endDate = Date(endMillis)
                    
                    val event = Event(
                        id = id.toString(),
                        title = title ?: "",
                        description = description,
                        startTime = Timestamp(startDate),
                        endTime = Timestamp(endDate),
                        calendarId = calendarId
                    )
                    
                    events.add(event)
                }
            }
            
            events
        }
    }
    
    // Функција за додавање догађаја у локални календар
    suspend fun addEventToCalendar(event: Event): Long {
        return withContext(Dispatchers.IO) {
            val contentResolver: ContentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                event.startTime?.let { put(CalendarContract.Events.DTSTART, it.seconds * 1000) }
                event.endTime?.let { put(CalendarContract.Events.DTEND, it.seconds * 1000) }
                put(CalendarContract.Events.CALENDAR_ID, 1) // Подразумевани календар
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            
            val uri: Uri? = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            
            uri?.let { ContentUris.parseId(it) } ?: -1
        }
    }
    
    // Функција за брисање догађаја из Firebase-а
    suspend fun deleteEventFromFirebase(eventId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("events")
                .document(eventId)
                .delete()
                .await()
            
            true
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при брисању догађаја из Firebase-а", e)
            false
        }
    }
    
    // Функција за брисање догађаја из локалног календара
    suspend fun deleteEventFromCalendar(eventId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver: ContentResolver = context.contentResolver
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
                val rows = contentResolver.delete(deleteUri, null, null)
                
                rows > 0
            } catch (e: Exception) {
                Log.e("EventRepository", "Грешка при брисању догађаја из календара", e)
                false
            }
        }
    }
    
    // Додавање новог догађаја
    suspend fun addEvent(event: Event): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            
            val eventData: Map<String, Any?> = mapOf(
                "title" to event.title,
                "description" to event.description,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "location" to event.location,
                "color" to event.color,
                "createdBy" to userId,
                "familyId" to event.familyId,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("events")
                .add(eventData)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при додавању догађаја", e)
            Result.failure(e)
        }
    }
    
    // Ажурирање постојећег догађаја
    suspend fun updateEvent(event: Event): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            val eventId = event.id ?: return Result.failure(IllegalArgumentException("Догађај нема ID"))
            
            val eventData: Map<String, Any?> = mapOf(
                "title" to event.title,
                "description" to event.description,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "location" to event.location,
                "color" to event.color,
                "familyId" to event.familyId
            )
            
            firestore.collection("users")
                .document(userId)
                .collection("events")
                .document(eventId)
                .update(eventData)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при ажурирању догађаја", e)
            Result.failure(e)
        }
    }
    
    // Добијање свих догађаја за тренутног корисника
    fun getEventsForCurrentUser() = callbackFlow {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            trySend(emptyList<Event>())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("users")
            .document(userId)
            .collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("EventRepository", "Грешка при праћењу догађаја корисника", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val event = doc.toObject(Event::class.java)
                        event?.id = doc.id
                        event
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Грешка при обради догађаја", e)
                        null
                    }
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Добијање свих догађаја за породицу
    fun getEventsForFamily(familyId: String): Flow<List<Event>> = callbackFlow {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            trySend(emptyList<Event>())
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("users")
            .document(userId)
            .collection("events")
            .whereEqualTo("familyId", familyId)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("EventRepository", "Грешка при праћењу породичних догађаја", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val event = doc.toObject(Event::class.java)
                        event?.id = doc.id
                        event
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Грешка при обради догађаја", e)
                        null
                    }
                } ?: emptyList()
                
                // Сортирамо догађаје локално уместо у упиту
                val sortedEvents = events.sortedBy { it.startTime?.seconds }
                
                trySend(sortedEvents)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Добијање догађаја за одређени временски период
    fun getEventsForPeriod(startDate: Date, endDate: Date): Flow<List<Event>> = callbackFlow {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            trySend(emptyList<Event>())
            close()
            return@callbackFlow
        }
        
        val startTimestamp = com.google.firebase.Timestamp(startDate)
        val endTimestamp = com.google.firebase.Timestamp(endDate)
        
        val listener = firestore.collection("users")
            .document(userId)
            .collection("events")
            .whereGreaterThanOrEqualTo("startTime", startTimestamp)
            .whereLessThanOrEqualTo("startTime", endTimestamp)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("EventRepository", "Грешка при праћењу догађаја за период", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val event = doc.toObject(Event::class.java)
                        event?.id = doc.id
                        event
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Грешка при обради догађаја", e)
                        null
                    }
                } ?: emptyList()
                
                // Сортирамо догађаје локално уместо у упиту
                val sortedEvents = events.sortedBy { it.startTime?.seconds }
                
                trySend(sortedEvents)
            }
        
        awaitClose { listener.remove() }
    }
    
    // Додајемо назад deleteEvent методу која користи deleteEventFromFirebase
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            val success = deleteEventFromFirebase(eventId)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Грешка при брисању догађаја"))
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при брисању догађаја", e)
            Result.failure(e)
        }
    }
} 