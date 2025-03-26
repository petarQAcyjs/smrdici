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
import com.google.firebase.firestore.ListenerRegistration
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
import javax.inject.Inject

class EventRepository @Inject constructor(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val eventsCollection = firestore.collection("calendar_events")
    private val prefs = context.getSharedPreferences("event_migration", Context.MODE_PRIVATE)
    private var eventsListener: ListenerRegistration? = null
    
    // Добијање тренутног корисника
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
    
    // Функција за синхронизацију догађаја
    suspend fun syncEvents(): Boolean {
        try {
            val userId = auth.currentUser?.uid ?: return false
            
            // Учитавамо догађаје директно из calendar_events колекције
            val events = eventsCollection
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Грешка при конверзији документа", e)
                        null
                    }
                }
            
            Log.d("EventRepository", "Синхронизовано ${events.size} догађаја")
            return true
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при синхронизацији догађаја", e)
            return false
        }
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
            
            val eventData = mapOf(
                "title" to event.title,
                "description" to event.description,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "location" to event.location,
                "color" to event.color,
                "assignee" to event.assignee,
                "createdBy" to userId,
                "createdAt" to Timestamp.now()
            )
            
            val docRef = eventsCollection.add(eventData).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при додавању догађаја", e)
            Result.failure(e)
        }
    }
    
    // Ажурирање догађаја
    suspend fun updateEvent(event: Event): Result<Unit> {
        return try {
            val eventData = mapOf(
                "title" to event.title,
                "description" to event.description,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "location" to event.location,
                "color" to event.color,
                "assignee" to event.assignee
            )
            
            eventsCollection.document(event.id ?: "")
                .update(eventData)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при ажурирању догађаја", e)
            Result.failure(e)
        }
    }
    
    // Брисање догађаја
    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            eventsCollection.document(eventId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при брисању догађаја", e)
            Result.failure(e)
        }
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
    
    // Функција за праћење догађаја
    fun observeEvents(): Flow<List<Event>> = callbackFlow {
        // Отказујемо претходни listener ако постоји
        eventsListener?.remove()
        
        eventsListener = eventsCollection
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("EventRepository", "Грешка при праћењу догађаја", error)
                    return@addSnapshotListener
                }

                val events = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Event::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e("EventRepository", "Грешка при конверзији документа", e)
                            null
                        }
                    } ?: emptyList()

                trySend(events)
            }

        // Чистимо listener када се проток откаже
        awaitClose {
            eventsListener?.remove()
            eventsListener = null
        }
    }.flowOn(Dispatchers.IO)
    
    // Додајте ову функцију у EventRepository
    suspend fun removeDuplicates() {
        val userId = auth.currentUser?.uid ?: return
        
        val events = eventsCollection
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Event::class.java)?.copy(id = it.id) }
        
        // Групишемо догађаје по јединственом кључу (наслов + време + додељена особа)
        val uniqueEvents = events.groupBy { 
            "${it.title}${it.startTime}${it.assignee}" 
        }
        
        // За сваку групу дупликата, задржавамо само најстарији
        uniqueEvents.forEach { (_, duplicates) ->
            if (duplicates.size > 1) {
                // Сортирамо по ID-у да бисмо задржали најстарији
                val sortedDuplicates = duplicates.sortedBy { it.id }
                // Задржавамо први, бришемо остале
                sortedDuplicates.drop(1).forEach { duplicate ->
                    duplicate.id?.let { id ->
                        try {
                            eventsCollection.document(id).delete().await()
                            Log.d("EventRepository", "Обрисан дупликат: ${duplicate.title}")
                        } catch (e: Exception) {
                            Log.e("EventRepository", "Грешка при брисању дупликата: ${duplicate.title}", e)
                        }
                    }
                }
            }
        }
    }
    
    // Додајте ову функцију у EventRepository
    suspend fun cleanupDatabase() {
        try {
            Log.d("EventRepository", "Почињем чишћење базе...")
            
            // 1. Прво добавимо све догађаје
            val snapshot = eventsCollection
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()
                
            val allEvents = snapshot.documents
                .mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Грешка при читању документа ${doc.id}", e)
                        null
                    }
                }
            
            Log.d("EventRepository", "Пронађено ${allEvents.size} догађаја")
            
            // 2. Групишемо догађаје по кључу
            val eventGroups = allEvents.groupBy { event ->
                "${event.title}_${event.startTime?.seconds}_${event.assignee}"
            }
            
            // 3. За сваку групу, задржавамо најстарији документ (са најмањим ID-ом)
            var deletedCount = 0
            eventGroups.forEach { (key, events) ->
                if (events.size > 1) {
                    // Сортирамо по ID-у и задржавамо први
                    val sortedEvents = events.sortedBy { it.id }
                    val keepEvent = sortedEvents.first()
                    
                    // Бришемо остале
                    sortedEvents.drop(1).forEach { duplicateEvent ->
                        duplicateEvent.id?.let { id ->
                            try {
                                eventsCollection.document(id).delete().await()
                                deletedCount++
                                Log.d("EventRepository", "Обрисан дупликат: ${duplicateEvent.title} (ID: $id)")
                            } catch (e: Exception) {
                                Log.e("EventRepository", "Грешка при брисању дупликата $id", e)
                            }
                        }
                    }
                    
                    Log.d("EventRepository", "Задржан оригинал: ${keepEvent.title} (ID: ${keepEvent.id})")
                }
            }
            
            Log.d("EventRepository", "Чишћење базе завршено. Обрисано $deletedCount дупликата")
            
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при чишћењу базе", e)
            throw e
        }
    }
    
    suspend fun getEvents(): Flow<List<Event>> = flow {
        try {
            val snapshot = eventsCollection
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()
                
            val events = snapshot.documents
                .mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Грешка при конверзији документа", e)
                        null
                    }
                }
                // Додајемо distinctBy да спречимо дупликате
                .distinctBy { "${it.title}${it.startTime}${it.assignee}" }
                
            Log.d("EventRepository", "Учитано ${events.size} догађаја из нове колекције")
            emit(events)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при учитавању догађаја", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
} 