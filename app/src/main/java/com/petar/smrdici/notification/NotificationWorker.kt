package com.petar.smrdici.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import java.util.Calendar
import java.util.Date

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
        val description = inputData.getString("EVENT_DESCRIPTION") ?: ""
        val startTimeMillis = inputData.getLong("EVENT_START_TIME", 0)
        val assignee = inputData.getString("EVENT_ASSIGNEE") ?: EventAssignee.EVERYONE.name
        
        if (startTimeMillis == 0L) {
            Log.e(TAG, "Invalid start time for event: $eventId")
            return Result.failure()
        }
        
        scheduleNotification(eventId, title, description, startTimeMillis, assignee)
        
        return Result.success()
    }
    
    private fun scheduleNotification(
        eventId: String,
        title: String,
        description: String,
        startTimeMillis: Long,
        assignee: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Schedule notification for 1 day before the event
        scheduleAlarm(
            alarmManager,
            eventId,
            title,
            description,
            startTimeMillis - ONE_DAY_MILLIS,
            assignee,
            1
        )
        
        // Schedule notification for the day of the event (1 hour before)
        scheduleAlarm(
            alarmManager,
            eventId,
            title,
            description,
            startTimeMillis - (60 * 60 * 1000), // 1 hour before
            assignee,
            2
        )
        
        Log.d(TAG, "Scheduled notifications for event: $title")
    }
    
    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        eventId: String,
        title: String,
        description: String,
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
            
            // Create appropriate message based on timing
            val message = if (requestCode == 1) {
                // 1 day before
                val assigneeName = try {
                    EventAssignee.valueOf(assignee).displayName
                } catch (e: Exception) {
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
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Alarm scheduled for ${Date(triggerAtMillis)}, event: $title")
    }
    
    private fun formatTime(timeMillis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timeMillis
        }
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return String.format("%02d:%02d", hour, minute)
    }
    
    class Builder(private val context: Context) {
        fun scheduleNotificationForEvent(event: Event) {
            if (event.id == null || event.startTime == null) {
                Log.e(TAG, "Cannot schedule notification for event with null id or start time")
                return
            }
            
            val startTimeMillis = event.startTime.toDate().time
            
            // Create notification one day before the event
            val notificationTime = startTimeMillis - ONE_DAY_MILLIS
            
            // Only schedule if the notification time is in the future
            if (notificationTime > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                // Schedule using the direct alarm approach
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("EVENT_ID", event.id)
                    putExtra("EVENT_TITLE", event.title)
                    
                    val assigneeName = try {
                        EventAssignee.valueOf(event.assignee).displayName
                    } catch (e: Exception) {
                        "Everyone"
                    }
                    
                    val message = "Hey $assigneeName, you have \"${event.title}\" tomorrow at ${
                        formatTime(event.startTime.toDate().time)
                    }"
                    
                    putExtra("EVENT_MESSAGE", message)
                }
                
                val uniqueRequestCode = "${event.id}_1".hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    uniqueRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                    )
                }
                
                Log.d(TAG, "Scheduled notification for event: ${event.title} at ${Date(notificationTime)}")
            } else {
                Log.d(TAG, "Skipping notification for past event: ${event.title}")
            }
        }
        
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
                
                Log.d(TAG, "Cancelled notifications for event: $eventId")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling notification", e)
            }
        }
        
        private fun formatTime(timeMillis: Long): String {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timeMillis
            }
            
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            return String.format("%02d:%02d", hour, minute)
        }
    }
} 