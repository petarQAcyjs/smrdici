package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Event
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Класа је измењена да прима зависности кроз конструктор уместо да их креира интерно
class EventRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val eventsCollection = firestore.collection("calendar_events")
    
    // Додајемо кеширање
    private var cachedEvents: List<Event> = emptyList()
    private var lastFetchTime: Long = 0
    
    // Функција за синхронизацију догађаја
    suspend fun syncEvents(): Result<Unit> {
        return try {
            auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
            val batch = firestore.batch()
            
            // 1. Прво учитавамо све догађаје из базе
            val remoteEvents = eventsCollection
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
            
            // 2. Учитавамо локалне промене
            val localChanges = getLocalChanges()
            
            // 3. Примењујемо batch операције
            localChanges.forEach { event ->
                val docRef = eventsCollection.document(event.id ?: eventsCollection.document().id)
                batch.set(docRef, event)
            }
            
            // 4. Извршавамо batch
            batch.commit().await()
            
            // 5. Инвалидирамо кеш
            lastFetchTime = 0
            cachedEvents = remoteEvents
            
            Log.d("EventRepository", "Синхронизовано ${remoteEvents.size} догађаја")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при синхронизацији", e)
            Result.failure(e)
        }
    }
    
    // Додавање новог догађаја
    suspend fun addEvent(event: Event): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            
            val eventData = mapOf(
                "title" to event.title,
                "description" to event.description,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "location" to event.location,
                "color" to event.color,
                "assignee" to event.assignee,
                "createdBy" to currentUserId,
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
            val eventData = hashMapOf(
                "title" to event.title,
                "description" to event.description,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "location" to event.location,
                "assignee" to event.assignee,
                "color" to event.color
            )
            
            Log.d("EventRepository", "Ажурирам догађај у бази: id=${event.id}, assignee=${event.assignee}")
            
            event.id?.let { id ->
                eventsCollection.document(id)
                    .update(eventData.toMap())
                    .await()
                
                Log.d("EventRepository", "Догађај успешно ажуриран у бази")
            } ?: throw IllegalStateException("Event ID cannot be null")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при ажурирању догађаја у бази", e)
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
    
    // Добављање свих догађаја за извоз/увоз
    suspend fun getAllEvents(): List<Event> {
        return try {
            // Proveravamo samo da li je korisnik prijavljen, ne čuvamo ID
            auth.currentUser?.uid ?: return emptyList()
            
            val snapshot = eventsCollection
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()
                
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e("EventRepository", "Грешка при читању догађаја ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при добављању свих догађаја", e)
            emptyList()
        }
    }
    
    // Брисање свих догађаја (за операцију увоза)
    suspend fun deleteAllEvents() {
        try {
            // Proveravamo samo da li je korisnik prijavljen
            auth.currentUser?.uid ?: return
            
            val snapshot = eventsCollection
                .get()
                .await()
                
            val batch = firestore.batch()
            for (document in snapshot.documents) {
                batch.delete(eventsCollection.document(document.id))
            }
            
            batch.commit().await()
            Log.d("EventRepository", "Сви догађаји су обрисани")
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при брисању свих догађаја", e)
        }
    }
    
    // Функција за функцију cleanupDatabase() и функцију за помоћне методе
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
            eventGroups.forEach { (_, events) ->
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
    
    // Помоћна функција за добављање локалних промена
    // Ово би требало да буде имплементирано за рад ван мреже
    private fun getLocalChanges(): List<Event> {
        // Овде бисмо имплементирали локални кеш са Room или неком другом базом
        return emptyList()
    }
    
    // Добављање догађаја између два датума
    fun getEvents(startDate: Date, endDate: Date): Flow<List<Event>> = callbackFlow {
        try {
            // Proveravamo samo da li je korisnik prijavljen
            val userId = auth.currentUser?.uid
            if (userId == null) {
                close(IllegalStateException("Корисник није пријављен"))
                return@callbackFlow
            }
            
            val startTimestamp = Timestamp(startDate)
            val endTimestamp = Timestamp(endDate)
            
            val listener = eventsCollection
                .whereGreaterThanOrEqualTo("startTime", startTimestamp)
                .whereLessThanOrEqualTo("startTime", endTimestamp)
                .orderBy("startTime", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("EventRepository", "Грешка при слушању догађаја за период", error)
                        
                        // Проверавамо да ли је грешка везана за недостајући индекс
                        if (error.message?.contains("FAILED_PRECONDITION") == true && 
                            error.message?.contains("The query requires an index") == true) {
                            
                            // Извлачимо URL за креирање индекса из поруке о грешци
                            val indexUrl = error.message?.let { msg ->
                                val urlPattern = "https://console\\.firebase\\.google\\.com\\S+".toRegex()
                                val matchResult = urlPattern.find(msg)
                                matchResult?.value
                            }
                            
                            Log.e("EventRepository", "Потребно је креирати индекс у Firebase конзоли. " +
                                   "Користите следећи линк: $indexUrl")
                            
                            // Шаљемо празну листу уместо да затворимо flow са грешком
                            trySend(emptyList())
                        } else {
                            close(error)
                        }
                        return@addSnapshotListener
                    }
                    
                    val events = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(Event::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e("EventRepository", "Грешка при читању догађаја ${doc.id}", e)
                            null
                        }
                    } ?: emptyList()
                    
                    trySend(events)
                }
            
            awaitClose { 
                listener.remove() 
                Log.d("EventRepository", "Затворен listener за догађаје")
            }
            
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при добављању догађаја", e)
            close(e)
        }
    }
    
    // Стара имплементација - оставља се због компатибилности
    suspend fun getEventsSync(startDate: Date, endDate: Date): Result<List<Event>> {
        return try {
            // Proveravamo samo da li je korisnik prijavljen
            auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Корисник није пријављен"))
            
            val startTimestamp = Timestamp(startDate)
            val endTimestamp = Timestamp(endDate)
            
            val snapshot = eventsCollection
                .whereGreaterThanOrEqualTo("startTime", startTimestamp)
                .whereLessThanOrEqualTo("startTime", endTimestamp)
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()
                
            val events = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Event::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e("EventRepository", "Грешка при читању догађаја ${doc.id}", e)
                    null
                }
            }
            
            Result.success(events)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при добављању догађаја", e)
            Result.failure(e)
        }
    }
    
    companion object {
        @Volatile
        private var instance: EventRepository? = null
        
        fun getInstance(): EventRepository {
            return instance ?: synchronized(this) {
                instance ?: EventRepository(
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance()
                ).also { instance = it }
            }
        }
    }
} 