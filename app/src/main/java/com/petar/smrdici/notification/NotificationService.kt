package com.petar.smrdici.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.petar.smrdici.R
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.EventAssignee
import com.petar.smrdici.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class NotificationService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "event_notifications"
        const val CHANNEL_NAME = "Event Notifications"
        const val CHANNEL_DESCRIPTION = "Notifications for upcoming events"

        const val EVENT_NOTIFICATION_ID_BASE = 1001
        const val GROUP_NOTIFICATION_ID = 9999

        private val random = Random()
        private val eventsByDate = ConcurrentHashMap<String, MutableList<EventNotificationData>>()
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
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
        val eventDate = extractDateFromMessage(message)
        val dateKey = dateFormatter.format(eventDate ?: Date())

        val eventData = EventNotificationData(
            eventId = eventId,
            title = title,
            message = message,
            assignee = assignee,
            date = eventDate
        )

        if (notificationManager.smartGroupingEnabled) {
            eventsByDate.getOrPut(dateKey) { mutableListOf() }.add(eventData)
            if ((eventsByDate[dateKey]?.size ?: 0) > 1) {
                showGroupedNotifications(dateKey)
                return
            }
        }

        showIndividualNotification(eventData)
    }

    private fun showIndividualNotification(eventData: EventNotificationData) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EVENT_ID", eventData.eventId)
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

        var notificationMessage = eventData.message

        if (notificationManager.weatherAwareEnabled) {
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
            .setSmallIcon(R.drawable.ic_notification)

        if (notificationManager.notificationSoundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        if (notificationManager.notificationVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }

        if (useAvatar) {
            try {
                val eventAssignee = EventAssignee.valueOf(eventData.assignee)
                val avatarIcon = createAssigneeAvatar(eventAssignee)
                builder.setLargeIcon(avatarIcon)
                builder.setColor(eventAssignee.color.toColorInt())
            } catch (e: Exception) {
                Log.e("NotificationService", "Error creating avatar for assignee: ${eventData.assignee}", e)
            }
        }

        val notificationId = EVENT_NOTIFICATION_ID_BASE + random.nextInt(1000)

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
                Log.d("NotificationService", "Notification shown: ${eventData.title} (ID: $notificationId)")
            } else {
                Log.e("NotificationService", "Notification permission not granted")
            }
        }
    }

    private fun showGroupedNotifications(dateKey: String) {
        val events = eventsByDate[dateKey] ?: return

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

        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Events").build())
            .setConversationTitle("Events for ${formatDate(events.firstOrNull()?.date)}")

        events.forEach { eventData ->
            val eventTimeStr = formatTime(eventData.date)
            val messageText = "${eventData.title} - $eventTimeStr"
            messagingStyle.addMessage(messageText, System.currentTimeMillis(), null as Person?)
        }

        val summaryText = "You have multiple events scheduled"
        var summaryWithWeather = summaryText
        if (notificationManager.weatherAwareEnabled) {
            val weatherPrefs = context.getSharedPreferences("weather_info", Context.MODE_PRIVATE)
            val weatherInfo = weatherPrefs.getString("weather_advice", null)
            if (weatherInfo != null) {
                summaryWithWeather += "\n\n🌤️ $weatherInfo"
            }
        }

        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${events.size} events today")
            .setContentText(summaryWithWeather)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(summaryPendingIntent)
            .setAutoCancel(true)
            .setGroupSummary(true)
            .setGroup(dateKey)

        if (notificationManager.notificationSoundEnabled) {
            summaryBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        if (notificationManager.notificationVibrationEnabled) {
            summaryBuilder.setVibrate(longArrayOf(0, 250, 250, 250))
        }

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(GROUP_NOTIFICATION_ID, summaryBuilder.build())
                Log.d("NotificationService", "Group notification shown for $dateKey with ${events.size} events")
            } else {
                Log.e("NotificationService", "Notification permission not granted")
            }
        }
    }

    private fun createAssigneeAvatar(assignee: EventAssignee): Bitmap {
        try {
            val drawableResId = when (assignee) {
                EventAssignee.EVERYONE -> R.drawable.avatar_everyone
                EventAssignee.PETAR -> R.drawable.avatar_petar
                EventAssignee.NATASA -> R.drawable.avatar_natasa
                EventAssignee.MILICA -> R.drawable.avatar_milica
                EventAssignee.BOGDAN -> R.drawable.avatar_bogdan
            }

            val drawable = context.resources.getDrawable(drawableResId, context.theme)
            val bitmap = createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            Log.e("NotificationService", "Error creating avatar from drawable", e)

            val size = 128
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val backgroundPaint = Paint().apply {
                color = assignee.color.toColorInt()
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = size / 2f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            canvas.drawCircle(size / 2f, size / 2f, size / 2f, backgroundPaint)
            val xPos = size / 2f
            val yPos = size / 2f - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(assignee.initial, xPos, yPos, textPaint)

            return bitmap
        }
    }

    private fun extractDateFromMessage(message: String): Date? {
        return try {
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

    private fun formatDate(date: Date?): String {
        if (date == null) return "Today"
        val formatter = SimpleDateFormat("EEEE, MMMM d", Locale.US)
        return formatter.format(date)
    }

    private fun formatTime(date: Date?): String {
        if (date == null) return ""
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        return formatter.format(date)
    }

    data class EventNotificationData(
        val eventId: String,
        val title: String,
        val message: String,
        val assignee: String,
        val date: Date?
    )

    fun showDailyWeatherNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "WEATHER_NOTIFICATION"
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            "daily_weather".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = SmrdiciApplication.getNotificationManager()
        val translatedMessage = translateWeatherMessage(message)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(translatedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(translatedMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createWeatherIcon())
            .setColor("#03A9F4".toColorInt())

        if (notificationManager.notificationSoundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        if (notificationManager.notificationVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }

        val notificationId = 2000

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
            } else {
                Log.e("NotificationService", "Notification permission not granted")
            }
        }
    }

    fun showDailyMorningNotification(title: String, message: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val source = if (message.contains("[Source:")) {
            message.substringAfter("[Source:").substringBefore("]").trim()
        } else if (message.contains("[Test:")) {
            message.substringAfter("[Test:").substringBefore("]").trim()
        } else {
            "Unknown"
        }

        Log.d("NotificationService", "Showing morning notification at $currentTime")
        Log.d("NotificationService", "Source: $source")
        Log.d("NotificationService", "Title: $title")
        Log.d("NotificationService", "Message: ${message.lines().first()}")

        val cleanMessage = message
            .replace(Regex("\\[Source:.*?\\]"), "")
            .replace(Regex("\\[Test:.*?\\]"), "")
            .trim()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "MORNING_NOTIFICATION"
            putExtra("NOTIFICATION_SOURCE", source)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            "daily_morning".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = SmrdiciApplication.getNotificationManager()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(cleanMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createMorningIcon())
            .setColor("#FF9800".toColorInt())

        if (notificationManager.notificationSoundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        if (notificationManager.notificationVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }

        val notificationId = 2001

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, builder.build())
                Log.d("NotificationService", "Morning notification shown: $title (Source: $source)")
            } else {
                Log.e("NotificationService", "Notification permission not granted")
            }
        }
    }

    private fun createMorningIcon(): Bitmap {
        val size = 128
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = "#FFEB3B".toColorInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = "#FF9800".toColorInt()
        paint.strokeWidth = 8f
        val centerX = size / 2f
        val centerY = size / 2f
        val rayLength = size * 0.45f
        val rayOuterLength = size * 0.35f

        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val startX = centerX + (rayLength * 0.4 * Math.cos(angle)).toFloat()
            val startY = centerY + (rayLength * 0.4 * Math.sin(angle)).toFloat()
            val endX = centerX + (rayOuterLength * Math.cos(angle)).toFloat()
            val endY = centerY + (rayOuterLength * Math.sin(angle)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, paint)
        }

        paint.color = "#795548".toColorInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f

        val smileRadius = size * 0.2f
        canvas.drawArc(
            centerX - smileRadius,
            centerY - smileRadius / 2,
            centerX + smileRadius,
            centerY + smileRadius,
            0f, 180f, false, paint
        )

        val eyeSize = size * 0.1f
        canvas.drawCircle(centerX - eyeSize, centerY - eyeSize, eyeSize / 4, paint)
        canvas.drawCircle(centerX + eyeSize, centerY - eyeSize, eyeSize / 4, paint)

        return bitmap
    }

    private fun createWeatherIcon(): Bitmap {
        val size = 128
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.rgb(66, 165, 245)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = Color.YELLOW
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f + 10, size / 4f, paint)
        canvas.drawCircle((size / 2f + 15), (size / 2f + 5), size / 5f, paint)
        canvas.drawCircle((size / 2f - 15), (size / 2f + 5), size / 5f, paint)

        return bitmap
    }

    private fun translateWeatherMessage(message: String): String {
        var result = message
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

        weatherTerms.forEach { (english, serbian) ->
            result = result.replace(english, serbian, ignoreCase = true)
        }

        return result
    }
}