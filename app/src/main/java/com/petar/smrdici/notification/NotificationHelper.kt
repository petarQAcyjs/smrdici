package com.petar.smrdici.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Helper class to easily schedule and manage notifications for calendar events
 */
class NotificationHelper(private val context: Context) {
    companion object {
        private const val TAG = "NotificationHelper"
        
        // Time constants
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        
        @Volatile
        private var instance: NotificationHelper? = null
        
        fun getInstance(context: Context?): NotificationHelper {
            if (context == null) {
                throw IllegalArgumentException("Context cannot be null")
            }
            
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Check if the app has permission to schedule exact alarms
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true // Before Android 12, permission was not required
            }
        }
        
        /**
         * Get intent to open system settings for exact alarm permission
         */
        fun getExactAlarmSettingsIntent(context: Context): Intent? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent().apply {
                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                null
            }
        }
    }
    
    /**
     * Schedule notifications for an event (day before and hour before)
     */
    fun scheduleNotificationsForEvent(event: Event) {
        Log.d(TAG, "Scheduling notifications for event: ${event.title}")
        
        // Validation check
        if (event.id == null || event.startTime == null) {
            Log.e(TAG, "Cannot schedule notifications: event has null id or start time")
            return
        }
        
        val eventStartTime = event.startTime.toDate().time
        val currentTime = System.currentTimeMillis()
        
        // Schedule day-before notification if applicable
        val dayBeforeTime = eventStartTime - ONE_DAY_MILLIS
        if (dayBeforeTime > currentTime) {
            scheduleNotification(
                event.id,
                event.title,
                "Сутра имате догађај: ${event.title}",
                dayBeforeTime,
                1
            )
            Log.d(TAG, "Scheduled day-before notification for ${event.title} at ${Date(dayBeforeTime)}")
        }
        
        // Schedule hour-before notification if applicable
        val hourBeforeTime = eventStartTime - ONE_HOUR_MILLIS
        if (hourBeforeTime > currentTime) {
            scheduleNotification(
                event.id,
                event.title,
                "За 1 сат почиње: ${event.title}",
                hourBeforeTime,
                2
            )
            Log.d(TAG, "Scheduled hour-before notification for ${event.title} at ${Date(hourBeforeTime)}")
        }
    }
    
    /**
     * Cancel all notifications for an event
     */
    fun cancelNotificationsForEvent(eventId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Cancel day-before notification
            val intent1 = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("EVENT_ID", eventId)
            }
            val pendingIntent1 = PendingIntent.getBroadcast(
                context,
                "${eventId}_1".hashCode(),
                intent1,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent1)
            
            // Cancel hour-before notification
            val intent2 = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("EVENT_ID", eventId)
            }
            val pendingIntent2 = PendingIntent.getBroadcast(
                context,
                "${eventId}_2".hashCode(),
                intent2,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent2)
            
            Log.d(TAG, "Cancelled all notifications for event ID: $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notifications for event $eventId", e)
        }
    }
    
    /**
     * Schedule a specific notification using AlarmManager
     */
    private fun scheduleNotification(
        eventId: String?,
        title: String,
        message: String,
        triggerAtMillis: Long,
        notificationType: Int
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("EVENT_ID", eventId)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_MESSAGE", message)
            }
            
            // Create a unique request code based on the event ID and notification type
            val requestCode = "${eventId}_$notificationType".hashCode()
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Check if we can use exact alarms
            val canUseExactAlarms = canScheduleExactAlarms(context)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canUseExactAlarms) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } catch (e: SecurityException) {
                        // Fall back to inexact alarm if permission is denied
                        Log.e(TAG, "Cannot use exact alarms, falling back to inexact", e)
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else {
                    // Use inexact alarm as fallback
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                if (canUseExactAlarms) {
                    try {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } catch (e: SecurityException) {
                        // Fall back to inexact alarm if permission is denied
                        Log.e(TAG, "Cannot use exact alarms, falling back to inexact", e)
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else {
                    // Use inexact alarm as fallback
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
            
            Log.d(TAG, "Successfully scheduled notification at ${Date(triggerAtMillis)}: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notification", e)
        }
    }
    
    /**
     * Schedule a test dynamic timing notification for debugging
     */
    fun scheduleTestDynamicNotification() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Schedule for 1 minute from now for testing
        val testTime = System.currentTimeMillis() + (60 * 1000) // 1 minute from now
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("EVENT_ID", "test_dynamic")
            putExtra("EVENT_TITLE", "Test Dynamic Event")
            putExtra("EVENT_MESSAGE", "This is a test dynamic timing notification")
            putExtra("EVENT_ASSIGNEE", "EVERYONE")
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            "test_dynamic".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (canScheduleExactAlarms(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    testTime,
                    pendingIntent
                )
                Log.d(TAG, "Test dynamic notification scheduled for ${Date(testTime)}")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    testTime,
                    pendingIntent
                )
                Log.d(TAG, "Test dynamic notification scheduled (inexact) for ${Date(testTime)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling test dynamic notification", e)
        }
    }

    /**
     * Schedule a morning notification at 8:00 AM for an event using local notifications
     */
    fun scheduleEventMorningNotification(event: Event) {
        // Only schedule if the event has a start time
        if (event.startTime == null) {
            Log.w(TAG, "Cannot schedule morning notification for event without start time: ${event.id}")
            return
        }
        
        val eventDate = event.startTime.toDate()
        
        // Create calendar instance for 8:00 AM on the event day
        val notificationTime = Calendar.getInstance().apply {
            time = eventDate
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Don't schedule if the notification time is in the past
        if (notificationTime.timeInMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Morning notification for event ${event.title} would be in the past, not scheduling")
            return
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Create intent for the notification
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_ASSIGNEE", event.assignee)
            
            // Create morning message
            val assigneeName = try {
                EventAssignee.valueOf(event.assignee).displayName
            } catch (_: Exception) {
                "Everyone"
            }
            
            val message = "Dobro jutro, $assigneeName! Danas imate događaj \"${event.title}\" u ${formatTime(eventDate)}"
            putExtra("EVENT_MESSAGE", message)
        }
        
        // Create unique request code for this notification
        val uniqueRequestCode = "${event.id}_morning".hashCode()
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Check if we can schedule exact alarms (Android 12+)
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        
        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime.timeInMillis,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime.timeInMillis,
                    pendingIntent
                )
            }
            
            Log.d(TAG, "Scheduled morning notification for event: ${event.title} at ${Date(notificationTime.timeInMillis)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule morning notification for event: ${event.title}", e)
        }
    }

    /**
     * Format time for display
     */
    private fun formatTime(date: Date): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
} 