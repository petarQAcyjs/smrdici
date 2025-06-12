package com.petar.smrdici.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petar.smrdici.R
import com.petar.smrdici.ui.MainActivity
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.model.EventAssignee
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.ContextCompat

class NotificationService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "event_notifications"
        const val CHANNEL_NAME = "Event Notifications"
        const val CHANNEL_DESCRIPTION = "Notifications for upcoming events"
        
        // Notification IDs
        const val EVENT_NOTIFICATION_ID_BASE = 1001
        const val GROUP_NOTIFICATION_ID = 9999
        
        private val random = Random()
        
        // Store events by date for grouping
        private val eventsByDate = ConcurrentHashMap<String, MutableList<EventNotificationData>>()
        
        // Date formatter for grouping key
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
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
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationService", "Created notification channel: $CHANNEL_ID")
        }
    }
    
    fun showEventNotification(eventId: String, title: String, message: String, assignee: String = EventAssignee.EVERYONE.name) {
        val notificationManager = SmrdiciApplication.getNotificationManager()
        
        // Extract event date from message (assuming it contains the date info)
        val eventDate = extractDateFromMessage(message)
        val dateKey = dateFormatter.format(eventDate ?: Date())
        
        // Store event data for potential grouping
        val eventData = EventNotificationData(
            eventId = eventId,
            title = title,
            message = message,
            assignee = assignee,
            date = eventDate
        )
        
        // Check if smart grouping is enabled
        if (notificationManager.smartGroupingEnabled) {
            // Add to events by date
            eventsByDate.getOrPut(dateKey) { mutableListOf() }.add(eventData)
            
            // If there are multiple events for this date, show grouped notification
            if (eventsByDate[dateKey]?.size ?: 0 > 1) {
                showGroupedNotifications(dateKey)
                return
            }
        }
        
        // Otherwise show individual notification
        showIndividualNotification(eventData)
    }
    
    private fun showIndividualNotification(eventData: EventNotificationData) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EVENT_ID", eventData.eventId)
            // Add action to identify this is coming from a notification
            action = "EVENT_NOTIFICATION"
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            eventData.eventId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationManager = SmrdiciApplication.getNotificationManager()
        val useAvatar = notificationManager.avatarNotificationsEnabled
        
        // Prepare notification message
        var notificationMessage = eventData.message
        
        // Add weather information if enabled
        if (notificationManager.weatherAwareEnabled) {
            // Get weather info from shared preferences
            val weatherPrefs = context.getSharedPreferences("weather_info", Context.MODE_PRIVATE)
            val weatherInfo = weatherPrefs.getString("weather_advice", null)
            
            if (weatherInfo != null) {
                notificationMessage += "\n\n🌤️ $weatherInfo"
            }
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(eventData.title)
            .setContentText(notificationMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification_png) // Use PNG notification icon
        
        // Set sound and vibration based on user preferences
        if (notificationManager.notificationSoundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        
        if (notificationManager.notificationVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }
        
        // Use avatar if enabled
        if (useAvatar) {
            try {
                val eventAssignee = EventAssignee.valueOf(eventData.assignee)
                val avatarIcon = createAssigneeAvatar(eventAssignee)
                builder.setLargeIcon(avatarIcon)
                
                // Set color based on assignee
                builder.setColor(Color.parseColor(eventAssignee.color))
            } catch (e: Exception) {
                Log.e("NotificationService", "Error creating avatar for assignee: ${eventData.assignee}", e)
            }
        }
        
        // Generate a unique notification ID based on the event ID and current time
        val notificationId = EVENT_NOTIFICATION_ID_BASE + random.nextInt(1000)
        
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(notificationId, builder.build())
                    Log.d("NotificationService", "Notification shown: ${eventData.title} (ID: $notificationId)")
                } else {
                    Log.e("NotificationService", "Notification permission not granted")
                }
            } else {
                // For Android versions prior to 13, no runtime permission needed
                notify(notificationId, builder.build())
                Log.d("NotificationService", "Notification shown (pre-13): ${eventData.title} (ID: $notificationId)")
            }
        }
    }
    
    private fun showGroupedNotifications(dateKey: String) {
        val events = eventsByDate[dateKey] ?: return
        
        // Create summary notification
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "CALENDAR_VIEW"
        }
        
        val summaryPendingIntent = PendingIntent.getActivity(
            context,
            "summary_$dateKey".hashCode(),
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationManager = SmrdiciApplication.getNotificationManager()
        
        // Create the message style notification with multiple messages
        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Events").build())
            .setConversationTitle("Events for ${formatDate(events.firstOrNull()?.date)}")
        
        // Add each event as a message without showing who they are for
        events.forEach { eventData ->
            // Format should be "Title - HH:MM" like in the screenshot
            val eventTimeStr = formatTime(eventData.date)
            val messageText = "${eventData.title} - $eventTimeStr"
            
            messagingStyle.addMessage(
                messageText,
                System.currentTimeMillis(),
                null as Person?
            )
        }
        
        // Build the summary notification
        val summaryText = "You have multiple events scheduled"
        
        // Add weather information if enabled
        var summaryWithWeather = summaryText
        if (notificationManager.weatherAwareEnabled) {
            // Get weather info from shared preferences
            val weatherPrefs = context.getSharedPreferences("weather_info", Context.MODE_PRIVATE)
            val weatherInfo = weatherPrefs.getString("weather_advice", null)
            
            if (weatherInfo != null) {
                summaryWithWeather += "\n\n🌤️ $weatherInfo"
            }
        }
        
        // Build the summary notification
        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_png) // Use PNG notification icon
            .setContentTitle("${events.size} events today")
            .setContentText(summaryWithWeather)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(summaryPendingIntent)
            .setAutoCancel(true)
            .setGroupSummary(true)
            .setGroup(dateKey)
        
        // Set sound and vibration based on user preferences
        if (notificationManager.notificationSoundEnabled) {
            summaryBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        
        if (notificationManager.notificationVibrationEnabled) {
            summaryBuilder.setVibrate(longArrayOf(0, 250, 250, 250))
        }
        
        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(GROUP_NOTIFICATION_ID, summaryBuilder.build())
                    Log.d("NotificationService", "Group notification shown for $dateKey with ${events.size} events")
                } else {
                    Log.e("NotificationService", "Notification permission not granted")
                }
            } else {
                notify(GROUP_NOTIFICATION_ID, summaryBuilder.build())
                Log.d("NotificationService", "Group notification shown for $dateKey with ${events.size} events")
            }
        }
    }
    
    /**
     * Creates a bitmap avatar for the given assignee
     */
    private fun createAssigneeAvatar(assignee: EventAssignee): Bitmap {
        try {
            // Get the appropriate drawable resource based on assignee
            val drawableResId = when (assignee) {
                EventAssignee.EVERYONE -> R.drawable.avatar_everyone
                EventAssignee.PETAR -> R.drawable.avatar_petar
                EventAssignee.NATASA -> R.drawable.avatar_natasa
                EventAssignee.MILICA -> R.drawable.avatar_milica
                EventAssignee.BOGDAN -> R.drawable.avatar_bogdan
            }
            
            // Convert drawable to bitmap
            val drawable = context.resources.getDrawable(drawableResId, context.theme)
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            
            return bitmap
        } catch (e: Exception) {
            Log.e("NotificationService", "Error creating avatar from drawable", e)
            
            // Fallback to the old method if drawable loading fails
            val size = 128 // Size of the avatar in pixels
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Background paint
            val backgroundPaint = Paint().apply {
                color = Color.parseColor(assignee.color)
                isAntiAlias = true
            }
            
            // Text paint
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = size / 2f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            
            // Draw circle background
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, backgroundPaint)
            
            // Draw text (initial)
            val xPos = size / 2f
            val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(assignee.initial, xPos, yPos, textPaint)
            
            return bitmap
        }
    }
    
    /**
     * Extract date from notification message
     */
    private fun extractDateFromMessage(message: String): Date? {
        return try {
            // This is a simple implementation - in a real app, you'd use a more robust approach
            // to extract the date from the message
            val now = Calendar.getInstance()
            
            if (message.contains("tomorrow")) {
                now.add(Calendar.DAY_OF_YEAR, 1)
                now.time
            } else {
                now.time
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Error extracting date from message", e)
            null
        }
    }
    
    /**
     * Format date for display
     */
    private fun formatDate(date: Date?): String {
        if (date == null) return "Today"
        
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Format time for display
     */
    private fun formatTime(date: Date?): String {
        if (date == null) return ""
        
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
    
    /**
     * Clear events for a specific date
     */
    fun clearEventsForDate(dateKey: String) {
        eventsByDate.remove(dateKey)
    }
    
    /**
     * Clear all stored events
     */
    fun clearAllEvents() {
        eventsByDate.clear()
    }
    
    /**
     * Data class to store event notification information
     */
    data class EventNotificationData(
        val eventId: String,
        val title: String,
        val message: String,
        val assignee: String,
        val date: Date?
    )
    
    /**
     * Shows a dedicated daily weather notification
     */
    fun showDailyWeatherNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add action to identify this is coming from a weather notification
            action = "WEATHER_NOTIFICATION"
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            "daily_weather".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationManager = SmrdiciApplication.getNotificationManager()
        
        // Translate any English weather descriptions in the message
        val translatedMessage = translateWeatherMessage(message)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(translatedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(translatedMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification_png)
        
        // Set weather icon as the large icon
        builder.setLargeIcon(createWeatherIcon())
        
        // Set a specific color for weather notifications
        builder.setColor(Color.parseColor("#03A9F4")) // Light Blue
        
        // Set sound and vibration based on user preferences
        if (notificationManager.notificationSoundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        
        if (notificationManager.notificationVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }
        
        // Use a fixed notification ID for weather notifications
        val notificationId = 2000
        
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(notificationId, builder.build())
                    Log.d("NotificationService", "Daily weather notification shown")
                } else {
                    Log.e("NotificationService", "Notification permission not granted")
                }
            } else {
                // For Android versions prior to 13, no runtime permission needed
                notify(notificationId, builder.build())
                Log.d("NotificationService", "Daily weather notification shown (pre-13)")
            }
        }
    }
    
    private fun createWeatherIcon(): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint().apply {
            color = Color.rgb(66, 165, 245) // Blue background
            style = Paint.Style.FILL
        }
        
        // Draw circle background
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Draw sun icon
        paint.color = Color.YELLOW
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        
        // Draw cloud
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f + 10, size / 4f, paint)
        canvas.drawCircle(size / 2f + 15, size / 2f + 5, size / 5f, paint)
        canvas.drawCircle(size / 2f - 15, size / 2f + 5, size / 5f, paint)
        
        return bitmap
    }
    
    /**
     * Translate any English weather descriptions in the message
     */
    private fun translateWeatherMessage(message: String): String {
        var result = message
        
        // Common weather descriptions to translate
        val weatherTerms = listOf(
            "Sunny" to "Сунчано",
            "Clear" to "Ведро",
            "Partly cloudy" to "Делимично облачно",
            "Cloudy" to "Облачно",
            "Overcast" to "Тмурно",
            "Mist" to "Измаглица",
            "Fog" to "Магла",
            "Light rain" to "Слаба киша",
            "Rain" to "Киша",
            "Heavy rain" to "Јака киша",
            "Thunderstorm" to "Грмљавина",
            "Thunder" to "Грмљавина",
            "Snow" to "Снег",
            "Light snow" to "Слаб снег",
            "Heavy snow" to "Јак снег",
            "Sleet" to "Суснежица",
            "Freezing" to "Ледено",
            "Drizzle" to "Росуља",
            "Hail" to "Град",
            "Shower" to "Пљусак"
        )
        
        // Replace each English term with its Serbian equivalent
        weatherTerms.forEach { (english, serbian) ->
            result = result.replace(english, serbian, ignoreCase = true)
        }
        
        return result
    }
} 