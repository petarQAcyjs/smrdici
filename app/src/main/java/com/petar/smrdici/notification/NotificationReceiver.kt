package com.petar.smrdici.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.EventAssignee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationReceiver"
        private const val NOTIFICATION_TYPE_DAILY_SUMMARY = "DAILY_SUMMARY"
        private const val NOTIFICATION_TYPE_EVENT = "EVENT"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val notificationType = intent.getStringExtra("NOTIFICATION_TYPE")
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        Log.d(TAG, "Received broadcast for notification at $currentTime, type: $notificationType")
        
        // Create a coroutine scope
        val scope = CoroutineScope(Dispatchers.Default)
        
        when (notificationType) {
            NOTIFICATION_TYPE_DAILY_SUMMARY -> {
                // Use goAsync to keep the process alive for async operations
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        handleDailyMorningNotification(context, intent)
                    } finally {
                        // Always finish the pending result
                        pendingResult.finish()
                    }
                }
            }
            else -> {
                handleEventNotification(context, intent)
            }
        }
    }
    
    private suspend fun handleDailyMorningNotification(context: Context, intent: Intent) {
        val title = intent.getStringExtra("EVENT_TITLE") ?: "Дневни преглед догађаја"
        val defaultMessage = intent.getStringExtra("EVENT_MESSAGE") ?: "Доброј јутро! Проверите данашње догађаје."
        
        // Log the morning notification trigger with source information
        Log.d(TAG, "Morning notification triggered at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
        Log.d(TAG, "Source: Scheduled 8:00 AM alarm")
        
        // Check if this is a test notification with pre-populated content
        if (defaultMessage.contains("[Test:") || defaultMessage.contains("🌡️")) {
            // For test notifications, use the provided content directly
            Log.d(TAG, "Using pre-populated test content")
            val notificationService = NotificationService(context)
            notificationService.showDailyMorningNotification(title, defaultMessage)
            return
        }
        
        try {
            // Get today's events for a more meaningful notification
            val eventsRepository = SmrdiciApplication.getInstance().getEventsRepository()
            val today = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time
            
            val tomorrow = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time
            
            // Now we can call suspend function properly
            val eventsResult = eventsRepository.getEventsSync(today, tomorrow)
            val events = if (eventsResult.isSuccess) eventsResult.getOrNull() ?: emptyList() else emptyList()
            
            val message = if (events.isEmpty()) {
                "Нема догађаја за данас. Имате слободан дан!"
            } else {
                val eventsList = events.take(5).joinToString("\n• ", prefix = "• ") { event -> 
                    val time = event.startTime?.toDate()?.let { date ->
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                    } ?: ""
                    
                    "${event.title} ${if (time.isNotEmpty()) "($time)" else ""}"
                }
                
                val suffix = if (events.size > 5) "\n\n...и још ${events.size - 5} догађаја" else ""
                "Данашњи догађаји:\n$eventsList$suffix"
            }
            
            // Add weather information - similar to the weather notification
            val enhancedMessage = "$message\n\n" +
                "🌡️ 22°C | 💨 5 km/h | 💧 60% | ☁️ Делимично облачно\n\n" +
                "Данас је добар дан за шетњу. Понесите лагану јакну!"
            
            // Show the notification
            val notificationService = NotificationService(context)
            notificationService.showDailyMorningNotification(title, enhancedMessage)
            
            Log.d(TAG, "Morning notification shown with ${events.size} events")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating morning notification with events", e)
            
            // Fallback to simple notification with weather info
            val weatherMessage = "$defaultMessage\n\n" +
                "🌡️ 22°C | 💨 5 km/h | 💧 60% | ☁️ Делимично облачно\n\n" +
                "Данас је добар дан за шетњу. Понесите лагану јакну!"
            
            val notificationService = NotificationService(context)
            notificationService.showDailyMorningNotification(title, weatherMessage)
        }
    }
    
    private fun handleEventNotification(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("EVENT_ID")
        val title = intent.getStringExtra("EVENT_TITLE")
        val message = intent.getStringExtra("EVENT_MESSAGE")
        val assignee = intent.getStringExtra("EVENT_ASSIGNEE") ?: EventAssignee.EVERYONE.name
        
        if (eventId == null || title == null || message == null) {
            Log.e(TAG, "Missing required data in event notification intent: eventId=$eventId, title=$title")
            return
        }
        
        Log.d(TAG, "Showing notification for event: $title, assignee: $assignee")
        
        val notificationService = NotificationService(context)
        notificationService.showEventNotification(eventId, title, message, assignee)
    }
} 