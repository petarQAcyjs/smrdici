package com.petar.smrdici.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

class NotificationManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "notification_preferences"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DAY_BEFORE_NOTIFICATION = "day_before_notification"
        private const val KEY_HOUR_BEFORE_NOTIFICATION = "hour_before_notification"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"
        private const val KEY_NOTIFICATION_VIBRATION = "notification_vibration"
        private const val KEY_AVATAR_NOTIFICATIONS = "avatar_notifications"
        private const val KEY_DYNAMIC_TIMING = "dynamic_timing"
        private const val KEY_SMART_GROUPING = "smart_grouping"
        private const val KEY_DAILY_MORNING = "daily_morning"
        
        const val TAG = "NotificationManager"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Default values: notifications are enabled, with both day-before and hour-before notifications
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_NOTIFICATIONS_ENABLED, value) }
            Log.d(TAG, "Notifications ${if (value) "enabled" else "disabled"}")
        }
    
    var dayBeforeNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAY_BEFORE_NOTIFICATION, true)
        set(value) = prefs.edit { putBoolean(KEY_DAY_BEFORE_NOTIFICATION, value) }
    
    var hourBeforeNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOUR_BEFORE_NOTIFICATION, true)
        set(value) = prefs.edit { putBoolean(KEY_HOUR_BEFORE_NOTIFICATION, value) }
    
    var notificationSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_SOUND, value) }
    
    var notificationVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_VIBRATION, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_VIBRATION, value) }
    
    // New notification features
    var avatarNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AVATAR_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_AVATAR_NOTIFICATIONS, value) }
    
    var dynamicTimingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_TIMING, false)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_TIMING, value) }
    
    var smartGroupingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_GROUPING, false)
        set(value) = prefs.edit { putBoolean(KEY_SMART_GROUPING, value) }

    var dailyMorningEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_MORNING, true)
        set(value) {
            prefs.edit { putBoolean(KEY_DAILY_MORNING, value) }
            Log.d(TAG, "Daily morning notifications ${if (value) "enabled" else "disabled"}")
        }

    /**
     * Cancel all notifications for an event
     */
    fun cancelEventNotifications(eventId: String) {
        val builder = NotificationWorker.Builder(context)
        builder.cancelNotificationsForEvent(eventId)
    }
    
    /**
     * Reset notification preferences to default values
     */
    fun resetToDefaults() {
        prefs.edit().apply {
            putBoolean(KEY_NOTIFICATIONS_ENABLED, true)
            putBoolean(KEY_DAY_BEFORE_NOTIFICATION, true)
            putBoolean(KEY_HOUR_BEFORE_NOTIFICATION, true)
            putBoolean(KEY_NOTIFICATION_SOUND, true)
            putBoolean(KEY_NOTIFICATION_VIBRATION, true)
            putBoolean(KEY_AVATAR_NOTIFICATIONS, true)
            putBoolean(KEY_DYNAMIC_TIMING, false)
            putBoolean(KEY_SMART_GROUPING, false)
            putBoolean(KEY_DAILY_MORNING, true)
            apply()
        }
        
        Log.d(TAG, "Notification preferences reset to defaults")
    }
    
    /**
     * Initialize daily notifications
     * Call this from the Application class or MainActivity onCreate
     */
    fun initializeDailyNotifications() {
        if (notificationsEnabled && dailyMorningEnabled) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                
                // Create calendar for 8:00 AM
                val morningCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 8)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    
                    // If it's already past 8 AM today, schedule for tomorrow
                    if (timeInMillis < System.currentTimeMillis()) {
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
                
                // Create intent for daily notification
                val intent = android.content.Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("NOTIFICATION_TYPE", "DAILY_SUMMARY")
                    putExtra("EVENT_TITLE", "Дневни преглед догађаја")
                    putExtra("EVENT_MESSAGE", "Доброј јутро! Проверите данашње догађаје.")
                }
                
                // Create pending intent with unique ID for daily notifications
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    "DAILY_MORNING_NOTIFICATION".hashCode(),
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                // Check if we can schedule exact alarms
                val canScheduleExact = NotificationHelper.canScheduleExactAlarms(context)

                // Schedule the alarm to repeat daily
                if (canScheduleExact) {
                    // Use exact alarm for more reliable timing
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        morningCalendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fall back to repeating alarm
                    alarmManager.setRepeating(
                        android.app.AlarmManager.RTC_WAKEUP,
                        morningCalendar.timeInMillis,
                        android.app.AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }

                Log.d(TAG, "Daily notifications scheduled for 8:00 AM, starting at ${java.util.Date(morningCalendar.timeInMillis)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize daily notifications", e)
            }
        } else {
            Log.d(TAG, "Daily notifications not initialized: enabled=${notificationsEnabled}, dailyMorning=${dailyMorningEnabled}")
        }
    }

}