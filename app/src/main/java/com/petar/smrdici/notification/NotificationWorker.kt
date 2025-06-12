package com.petar.smrdici.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.util.ApiKeys
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "NotificationWorker"
        
        // Notification timing constants (in milliseconds)
        const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        
        /**
         * Translate common weather descriptions from English to Serbian Cyrillic
         */
        fun translateWeatherDescription(description: String): String {
            return when {
                description.contains("Sunny", ignoreCase = true) -> "Сунчано"
                description.contains("Clear", ignoreCase = true) -> "Ведро"
                description.contains("Partly cloudy", ignoreCase = true) -> "Делимично облачно"
                description.contains("Cloudy", ignoreCase = true) -> "Облачно"
                description.contains("Overcast", ignoreCase = true) -> "Тмурно"
                description.contains("Mist", ignoreCase = true) -> "Измаглица"
                description.contains("Fog", ignoreCase = true) -> "Магла"
                description.contains("Light rain", ignoreCase = true) -> "Слаба киша"
                description.contains("Rain", ignoreCase = true) -> "Киша"
                description.contains("Heavy rain", ignoreCase = true) -> "Јака киша"
                description.contains("Thunderstorm", ignoreCase = true) -> "Грмљавина"
                description.contains("Thunder", ignoreCase = true) -> "Грмљавина"
                description.contains("Snow", ignoreCase = true) -> "Снег"
                description.contains("Light snow", ignoreCase = true) -> "Слаб снег"
                description.contains("Heavy snow", ignoreCase = true) -> "Јак снег"
                description.contains("Sleet", ignoreCase = true) -> "Суснежица"
                description.contains("Freezing", ignoreCase = true) -> "Ледено"
                description.contains("Drizzle", ignoreCase = true) -> "Росуља"
                description.contains("Hail", ignoreCase = true) -> "Град"
                description.contains("Shower", ignoreCase = true) -> "Пљусак"
                else -> description // Return original if no translation found
            }
        }
    }
    
    override suspend fun doWork(): Result {
        val eventId = inputData.getString("EVENT_ID") ?: return Result.failure()
        val title = inputData.getString("EVENT_TITLE") ?: return Result.failure()
        val description = inputData.getString("EVENT_DESCRIPTION") ?: ""
        val startTimeMillis = inputData.getLong("EVENT_START_TIME", 0)
        val assignee = inputData.getString("EVENT_ASSIGNEE") ?: EventAssignee.EVERYONE.name
        val location = inputData.getString("EVENT_LOCATION") ?: "Београд" // Default location
        
        if (startTimeMillis == 0L) {
            Log.e(TAG, "Invalid start time for event: $eventId")
            return Result.failure()
        }
        
        // Check if weather-aware notifications are enabled
        val notificationManager = SmrdiciApplication.getNotificationManager()
        if (notificationManager.weatherAwareEnabled) {
            fetchWeatherInfo(location)
        }
        
        scheduleNotification(eventId, title, description, startTimeMillis, assignee)
        
        return Result.success()
    }
    
    /**
     * Fetch weather information and store it in SharedPreferences
     */
    private suspend fun fetchWeatherInfo(location: String) {
        try {
            val weatherService = WeatherService(context)
            val apiKey = ApiKeys.WEATHER_API_KEY
            
            val weatherInfo = weatherService.getWeatherInfo(location, apiKey)
            if (weatherInfo != null) {
                // Get weather advice
                val weatherAdvice = weatherService.getWeatherAdvice(weatherInfo)
                
                // Store in SharedPreferences for the notification service to use
                val weatherPrefs = context.getSharedPreferences("weather_info", Context.MODE_PRIVATE)
                weatherPrefs.edit().apply {
                    putString("weather_advice", weatherAdvice)
                    putString("weather_description", weatherInfo.description)
                    putFloat("weather_temperature", weatherInfo.temperature.toFloat())
                    apply()
                }
                
                Log.d(TAG, "Weather info fetched: $weatherAdvice")
            } else {
                Log.e(TAG, "Failed to fetch weather info")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather info", e)
        }
    }
    
    private fun scheduleNotification(
        eventId: String,
        title: String,
        description: String,
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
                description,
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
                    description,
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
                    description,
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
        description: String,
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
        
        // Only schedule if the notification time is in the future
        if (notificationTime.timeInMillis > System.currentTimeMillis()) {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("EVENT_ID", eventId)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_ASSIGNEE", assignee)
                
                // Create appropriate message
                val assigneeName = try {
                    EventAssignee.valueOf(assignee).displayName
                } catch (e: Exception) {
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
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime.timeInMillis,
                    pendingIntent
                )
            }
            
            Log.d(TAG, "Scheduled dynamic notification for ${Date(notificationTime.timeInMillis)}, event: $title at ${Date(startTimeMillis)}")
        } else {
            Log.d(TAG, "Skipping dynamic notification for past time: ${Date(notificationTime.timeInMillis)}")
        }
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
            putExtra("EVENT_ASSIGNEE", assignee)
            
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
            
            val notificationManager = SmrdiciApplication.getNotificationManager()
            val startTimeMillis = event.startTime.toDate().time
            
            // Check if weather-aware notifications are enabled
            if (notificationManager.weatherAwareEnabled) {
                // Fetch weather in a coroutine
                GlobalScope.launch {
                    try {
                        val weatherService = WeatherService(context)
                        val apiKey = ApiKeys.WEATHER_API_KEY
                        
                        val location = event.location.ifEmpty { "Београд" } // Default location
                        val weatherInfo = weatherService.getWeatherInfo(location, apiKey)
                        
                        if (weatherInfo != null) {
                            // Get weather advice
                            val weatherAdvice = weatherService.getWeatherAdvice(weatherInfo)
                            
                            // Store in SharedPreferences for the notification service to use
                            val weatherPrefs = context.getSharedPreferences("weather_info", Context.MODE_PRIVATE)
                            weatherPrefs.edit().apply {
                                putString("weather_advice", weatherAdvice)
                                putString("weather_description", weatherInfo.description)
                                putFloat("weather_temperature", weatherInfo.temperature.toFloat())
                                apply()
                            }
                            
                            Log.d(TAG, "Weather info fetched for event: ${event.title}, advice: $weatherAdvice")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching weather info for event: ${event.title}", e)
                    }
                }
            }
            
            // Check if dynamic timing is enabled
            if (notificationManager.dynamicTimingEnabled) {
                // Schedule dynamic notification
                scheduleDynamicNotification(event)
            } else {
                // Schedule standard notifications (day before)
                if (notificationManager.dayBeforeNotificationEnabled) {
                    scheduleDayBeforeNotification(event)
                }
                
                // Schedule hour before notification if enabled
                if (notificationManager.hourBeforeNotificationEnabled) {
                    scheduleHourBeforeNotification(event)
                }
            }
        }
        
        private fun scheduleDynamicNotification(event: Event) {
            val startTimeMillis = event.startTime!!.toDate().time
            
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
            
            // Only schedule if the notification time is in the future
            if (notificationTime.timeInMillis > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("EVENT_ID", event.id)
                    putExtra("EVENT_TITLE", event.title)
                    putExtra("EVENT_ASSIGNEE", event.assignee)
                    
                    val assigneeName = try {
                        EventAssignee.valueOf(event.assignee).displayName
                    } catch (e: Exception) {
                        "Everyone"
                    }
                    
                    val message = if (eventTime.get(Calendar.HOUR_OF_DAY) < 12) {
                        "Hey $assigneeName, you have \"${event.title}\" today at ${
                            formatTime(startTimeMillis)
                        }"
                    } else {
                        "Hey $assigneeName, you have \"${event.title}\" this afternoon at ${
                            formatTime(startTimeMillis)
                        }"
                    }
                    
                    putExtra("EVENT_MESSAGE", message)
                }
                
                val uniqueRequestCode = "${event.id}_dynamic".hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    uniqueRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime.timeInMillis,
                        pendingIntent
                    )
                }
                
                Log.d(TAG, "Scheduled dynamic notification for ${Date(notificationTime.timeInMillis)}, event: ${event.title}")
            }
        }
        
        private fun scheduleDayBeforeNotification(event: Event) {
            val startTimeMillis = event.startTime!!.toDate().time
            val notificationTime = startTimeMillis - ONE_DAY_MILLIS
            
            // Only schedule if the notification time is in the future
            if (notificationTime > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                // Schedule using the direct alarm approach
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("EVENT_ID", event.id)
                    putExtra("EVENT_TITLE", event.title)
                    putExtra("EVENT_ASSIGNEE", event.assignee)
                    
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
                
                Log.d(TAG, "Scheduled day-before notification for event: ${event.title} at ${Date(notificationTime)}")
            }
        }
        
        private fun scheduleHourBeforeNotification(event: Event) {
            val startTimeMillis = event.startTime!!.toDate().time
            val notificationTime = startTimeMillis - (60 * 60 * 1000) // 1 hour before
            
            // Only schedule if the notification time is in the future
            if (notificationTime > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                // Schedule using the direct alarm approach
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("EVENT_ID", event.id)
                    putExtra("EVENT_TITLE", event.title)
                    putExtra("EVENT_ASSIGNEE", event.assignee)
                    
                    val message = "Reminder: \"${event.title}\" starts in 1 hour at ${
                        formatTime(event.startTime.toDate().time)
                    }"
                    
                    putExtra("EVENT_MESSAGE", message)
                }
                
                val uniqueRequestCode = "${event.id}_2".hashCode()
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
                
                Log.d(TAG, "Scheduled hour-before notification for event: ${event.title} at ${Date(notificationTime)}")
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