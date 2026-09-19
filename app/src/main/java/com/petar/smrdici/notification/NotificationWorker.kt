package com.petar.smrdici.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.EventAssignee
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "NotificationWorker"
        
        // Notification timing constants (in milliseconds)
        const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
    
    override suspend fun doWork(): Result {
        val eventId = inputData.getString("EVENT_ID") ?: return Result.failure()
        val title = inputData.getString("EVENT_TITLE") ?: return Result.failure()
        val startTimeMillis = inputData.getLong("EVENT_START_TIME", 0)
        val assignee = inputData.getString("EVENT_ASSIGNEE") ?: EventAssignee.EVERYONE.name

        if (startTimeMillis == 0L) {
            Log.e(TAG, "Invalid start time for event: $eventId")
            return Result.failure()
        }
        
        scheduleNotification(eventId, title, startTimeMillis, assignee)
        
        return Result.success()
    }

    private fun scheduleNotification(
        eventId: String,
        title: String,
        startTimeMillis: Long,
        assignee: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = SmrdiciApplication.getNotificationManager()
        
        // Check if dynamic timing is enabled
        if (notificationManager.dynamicTimingEnabled) {
            // Schedule using dynamic timing
            scheduleDynamicNotification(
                alarmManager,
                eventId,
                title,
                startTimeMillis,
                assignee
            )
        } else {
            // Schedule using standard timing (day before and hour before)
            if (notificationManager.dayBeforeNotificationEnabled) {
                scheduleAlarm(
                    alarmManager,
                    eventId,
                    title,
                    startTimeMillis - ONE_DAY_MILLIS,
                    assignee,
                    1
                )
            }
            
            if (notificationManager.hourBeforeNotificationEnabled) {
                scheduleAlarm(
                    alarmManager,
                    eventId,
                    title,
                    startTimeMillis - (60 * 60 * 1000), // 1 hour before
                    assignee,
                    2
                )
            }
        }
        
        Log.d(TAG, "Scheduled notifications for event: $title")
    }
    
    /**
     * Schedule notification using dynamic timing:
     * - For events before noon, send at 8:00 AM on the day of event
     * - For events after 12:59 PM, send at noon
     */
    private fun scheduleDynamicNotification(
        alarmManager: AlarmManager,
        eventId: String,
        title: String,
        startTimeMillis: Long,
        assignee: String
    ) {
        // Get the event start time as Calendar
        val eventTime = Calendar.getInstance().apply {
            timeInMillis = startTimeMillis
        }
        
        // Create calendar for notification time
        val notificationTime = Calendar.getInstance().apply {
            // Set to the same day as the event
            set(Calendar.YEAR, eventTime.get(Calendar.YEAR))
            set(Calendar.MONTH, eventTime.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, eventTime.get(Calendar.DAY_OF_MONTH))
            
            // Check if event is before or after noon
            if (eventTime.get(Calendar.HOUR_OF_DAY) < 12) {
                // Event is before noon, notify at 8:00 AM
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            } else {
                // Event is after noon, notify at 12:00 PM
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
        }
        
        Log.d(TAG, "Dynamic timing calculation: Event at ${Date(startTimeMillis)}, Notification scheduled for ${Date(notificationTime.timeInMillis)}")
        
        // Only schedule if the notification time is in the future
        if (notificationTime.timeInMillis > System.currentTimeMillis()) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("EVENT_ID", eventId)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_ASSIGNEE", assignee)
                
                // Create appropriate message
                val assigneeName = try {
                    EventAssignee.valueOf(assignee).displayName
                } catch (_: Exception) {
                    "Everyone"
                }
                
                val message = if (eventTime.get(Calendar.HOUR_OF_DAY) < 12) {
                    "Hey $assigneeName, you have \"$title\" today at ${formatTime(startTimeMillis)}"
                } else {
                    "Hey $assigneeName, you have \"$title\" this afternoon at ${formatTime(startTimeMillis)}"
                }
                
                putExtra("EVENT_MESSAGE", message)
            }
            
            val uniqueRequestCode = "${eventId}_dynamic".hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Check if we can schedule exact alarms (Android 12+)
            val canScheduleExact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            
            Log.d(TAG, "Can schedule exact alarms: $canScheduleExact")
            
            try {
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for dynamic notification: ${Date(notificationTime.timeInMillis)}")
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled inexact alarm (fallback) for dynamic notification: ${Date(notificationTime.timeInMillis)}")
                }
                
                Log.d(TAG, "Successfully scheduled dynamic notification for ${Date(notificationTime.timeInMillis)}, event: $title")
            } catch (exception: Exception) {
                Log.e(TAG, "Error scheduling dynamic notification for event: $title", exception)
            }
        } else {
            Log.d(TAG, "Skipping dynamic notification for past time: ${Date(notificationTime.timeInMillis)}")
        }
    }
    
    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        eventId: String,
        title: String,
        triggerAtMillis: Long,
        assignee: String,
        requestCode: Int
    ) {
        // Don't schedule if the time is already in the past
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Skipping notification for past time: $triggerAtMillis")
            return
        }
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("EVENT_ID", eventId)
            putExtra("EVENT_TITLE", title)
            putExtra("EVENT_ASSIGNEE", assignee)
            
            // Create appropriate message based on timing
            val message = if (requestCode == 1) {
                // 1 day before
                val assigneeName = try {
                    EventAssignee.valueOf(assignee).displayName
                } catch (_: Exception) {
                    "Everyone"
                }
                
                "Hey $assigneeName, you have \"$title\" tomorrow at ${formatTime(triggerAtMillis + ONE_DAY_MILLIS)}"
            } else {
                // Same day (1 hour before)
                "Reminder: \"$title\" starts in 1 hour at ${formatTime(triggerAtMillis + (60 * 60 * 1000))}"
            }
            
            putExtra("EVENT_MESSAGE", message)
        }
        
        val uniqueRequestCode = "${eventId}_$requestCode".hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        
        Log.d(TAG, "Alarm scheduled for ${Date(triggerAtMillis)}, event: $title")
    }
    
    private fun formatTime(timeMillis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timeMillis
        }
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }
    
    class Builder(private val context: Context) {

        fun cancelNotificationsForEvent(eventId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Cancel day-before notification
            val intent1 = Intent(context, NotificationReceiver::class.java)
            val pendingIntent1 = PendingIntent.getBroadcast(
                context,
                "${eventId}_1".hashCode(),
                intent1,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                alarmManager.cancel(pendingIntent1)
                
                // Cancel same-day notification
                val intent2 = Intent(context, NotificationReceiver::class.java)
                val pendingIntent2 = PendingIntent.getBroadcast(
                    context,
                    "${eventId}_2".hashCode(),
                    intent2,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent2)
                
                // Cancel dynamic notification
                val intentDynamic = Intent(context, NotificationReceiver::class.java)
                val pendingIntentDynamic = PendingIntent.getBroadcast(
                    context,
                    "${eventId}_dynamic".hashCode(),
                    intentDynamic,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntentDynamic)
                
                Log.d(TAG, "Cancelled notifications for event: $eventId")
            } catch (exception: Exception) {
                Log.e(TAG, "Error cancelling notification", exception)
            }
        }
        
        private fun formatTime(timeMillis: Long): String {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timeMillis
            }
            
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }
    }
} 