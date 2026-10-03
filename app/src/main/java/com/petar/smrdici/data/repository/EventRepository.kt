package com.petar.smrdici.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.notification.NotificationHelper
import com.petar.smrdici.notification.NotificationManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

// Класа је измењена да прима зависности кроз конструктор уместо да их креира интерно
class EventRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context? = null
) {
    private val eventsCollection = firestore.collection("calendar_events")
    
    // Додајемо кеширање
    private var cachedEvents: List<Event> = emptyList()
    private var lastFetchTime: Long = 0
    
    // Notification manager for event notifications
    private val notificationManager = context?.let { NotificationManager(it) }
    
    // Функција за синхронизацију догађаја
    suspend fun syncEvents(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("Корисник није пријављен")
            Log.d("EventRepository", "Почињем синхронизацију за корисника: $userId")
            
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
            
            Log.d("EventRepository", "Учитано ${remoteEvents.size} догађаја из Firestore базе")
            
            // 2. Учитавамо све локалне догађаје
            val localEvents = getAllEvents()
            Log.d("EventRepository", "Учитано ${localEvents.size} локалних догађаја")
            
            // 3. Идентификујемо догађаје који постоје само локално
            val localOnlyEvents = localEvents.filter { localEvent -> 
                remoteEvents.none { it.id == localEvent.id }
            }
            Log.d("EventRepository", "Пронађено ${localOnlyEvents.size} догађаја који постоје само локално")
            
            // 4. Креирамо batch операцију за слање локалних догађаја на сервер
            if (localOnlyEvents.isNotEmpty()) {
                val batch = firestore.batch()
                
                localOnlyEvents.forEach { event ->
                    val docRef = if (event.id != null) {
                        eventsCollection.document(event.id!!)
                    } else {
                        eventsCollection.document()
                    }
                    batch.set(docRef, event)
                }
                
                batch.commit().await()
                Log.d("EventRepository", "Послато ${localOnlyEvents.size} локалних догађаја на сервер")
            }
            
            // 5. Идентификујемо догађаје који постоје само на серверу
            val remoteOnlyEvents = remoteEvents.filter { remoteEvent ->
                localEvents.none { it.id == remoteEvent.id }
            }
            Log.d("EventRepository", "Пронађено ${remoteOnlyEvents.size} догађаја који постоје само на серверу")
            
            // 6. Ажурирамо локални кеш са свим догађајима
            cachedEvents = (localEvents + remoteOnlyEvents).distinctBy { it.id }
            lastFetchTime = System.currentTimeMillis()
            
            // 7. Cancel notifications for past events
            cancelPastEventNotifications(cachedEvents)
            
            // 8. Schedule notifications for upcoming events
            scheduleNotificationsForEvents(cachedEvents)
            
            Log.d("EventRepository", "Синхронизација завршена. Укупно догађаја након синхронизације: ${cachedEvents.size}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при синхронизацији", e)
            Result.failure(e)
        }
    }

    fun getAllEventsFlow(): Flow<List<Event>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close(IllegalStateException("Korisnik nije prijavljen"))
            return@callbackFlow
        }

        val listener = eventsCollection
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("EventRepository", "Greška pri slušanju svih događaja", error)
                    trySend(cachedEvents) // Vrati bar ono što imamo u kešu
                    return@addSnapshotListener
                }

                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("EventRepository", "Greška pri konverziji dokumenta ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                cachedEvents = events
                lastFetchTime = System.currentTimeMillis()
                trySend(events)
            }

        awaitClose {
            listener.remove()
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
                "createdAt" to Timestamp.now(),
                "isRecurringYearly" to event.isRecurringYearly
            )
            
            val docRef = eventsCollection.add(eventData).await()
            
            // Schedule notification for the new event
            val newEvent = event.copy(id = docRef.id)
            scheduleNotificationForEvent(newEvent)
            
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
                "color" to event.color,
                "isRecurringYearly" to event.isRecurringYearly
            )
            
            Log.d("EventRepository", "Ажурирам догађај у бази: id=${event.id}, assignee=${event.assignee}, isRecurringYearly=${event.isRecurringYearly}")
            
            event.id?.let { id ->
                eventsCollection.document(id)
                    .update(eventData.toMap())
                    .await()
                
                // Cancel existing notifications and reschedule
                notificationManager?.cancelEventNotifications(id)
                
                // Only schedule if the event is in the future
                if (isEventInFuture(event)) {
                    scheduleNotificationForEvent(event)
                    Log.d("EventRepository", "Заказано обавештење за будући догађај: ${event.title}")
                } else {
                    Log.d("EventRepository", "Догађај је у прошлости, обавештења нису заказана: ${event.title}")
                }
                
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
            
            // Cancel notifications for deleted event
            notificationManager?.cancelEventNotifications(eventId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Грешка при брисању догађаја", e)
            Result.failure(e)
        }
    }
    
    // Schedule notification for a single event
    private fun scheduleNotificationForEvent(event: Event) {
        // Only schedule notifications for future events
        if (isEventInFuture(event) && event.id != null) {
            context?.let { ctx ->
                val notificationHelper = NotificationHelper.getInstance(ctx)
                notificationHelper.scheduleNotificationsForEvent(event)
                Log.d("EventRepository", "Scheduled notifications for event: ${event.title}")
            } ?: Log.e("EventRepository", "Context is null, cannot schedule notifications")
        } else {
            Log.d("EventRepository", "Прескачем заказивање обавештења за прошли догађај: ${event.title}")
        }
    }
    
    // Schedule notifications for multiple events
    private fun scheduleNotificationsForEvents(events: List<Event>) {
        val futureEvents = events.filter { isEventInFuture(it) }
        Log.d("EventRepository", "Заказујем обавештења за ${futureEvents.size} будућих догађаја од укупно ${events.size}")
        
        futureEvents.forEach { event ->
            scheduleNotificationForEvent(event)
        }
    }
    
    // Cancel notifications for events that are in the past
    private fun cancelPastEventNotifications(events: List<Event>) {
        val pastEvents = events.filter { !isEventInFuture(it) }
        Log.d("EventRepository", "Отказујем обавештења за ${pastEvents.size} прошлих догађаја")
        
        pastEvents.forEach { event ->
            event.id?.let { eventId ->
                notificationManager?.cancelEventNotifications(eventId)
                Log.d("EventRepository", "Отказано обавештење за прошли догађај: ${event.title}")
            }
        }
    }
    
    // Check if an event is in the future
    private fun isEventInFuture(event: Event): Boolean {
        val now = System.currentTimeMillis()
        return event.startTime?.toDate()?.time?.let { it > now } == true
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
    
    /**
     * Schedule a daily morning notification for events using a hybrid approach
     * - Uses local notifications if the app is running
     * - Uses FCM for background notifications when the app is closed
     */
    fun scheduleEventMorningNotification(event: Event) {
        Log.d(TAG, "Scheduling morning notification for event: ${event.title}")
        
        // First, schedule local notification with AlarmManager
        context?.let { ctx ->
            val notificationHelper = NotificationHelper.getInstance(ctx)
            notificationHelper.scheduleEventMorningNotification(event)
            
            // Then, also schedule FCM notification via Firestore
            if (event.startTime != null) {
                val userId = SmrdiciApplication.getInstance().getCurrentUserId()
                
                if (userId.isNotEmpty()) {
                    val morningTime = Calendar.getInstance().apply {
                        time = event.startTime.toDate()
                        set(Calendar.HOUR_OF_DAY, 8)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        
                        // If the event is today, but it's already past 8 AM, don't schedule
                        if (timeInMillis < System.currentTimeMillis()) {
                            return@scheduleEventMorningNotification
                        }
                    }
                    
                    val notification = hashMapOf(
                        "title" to "Dnevni podsetnik",
                        "message" to "Danas imate događaj: ${event.title}",
                        "eventId" to event.id,
                        "recipients" to listOf(userId),
                        "status" to "scheduled",
                        "scheduledFor" to Timestamp(Date(morningTime.timeInMillis)),
                        "createdAt" to Timestamp.now()
                    )
                    
                    // Add to Firestore notifications collection
                    FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .add(notification)
                        .addOnSuccessListener {
                            Log.d(TAG, "FCM notification scheduled for event: ${event.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to schedule FCM notification", e)
                        }
                }
            }
        } ?: Log.e(TAG, "Cannot schedule notifications: context is null")
    }
    
    companion object {
        private const val TAG = "EventRepository"
        
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
        
        fun getInstance(context: Context): EventRepository {
            return instance ?: synchronized(this) {
                instance ?: EventRepository(
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance(),
                    context
                ).also { instance = it }
            }
        }
    }
} 