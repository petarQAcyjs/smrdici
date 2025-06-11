package com.petar.smrdici.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.petar.smrdici.data.model.Event

class NotificationManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "notification_preferences"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DAY_BEFORE_NOTIFICATION = "day_before_notification"
        private const val KEY_HOUR_BEFORE_NOTIFICATION = "hour_before_notification"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"
        private const val KEY_NOTIFICATION_VIBRATION = "notification_vibration"
        
        const val TAG = "NotificationManager"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Default values: notifications are enabled, with both day-before and hour-before notifications
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()
            Log.d(TAG, "Notifications ${if (value) "enabled" else "disabled"}")
        }
    
    var dayBeforeNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAY_BEFORE_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_DAY_BEFORE_NOTIFICATION, value).apply()
    
    var hourBeforeNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOUR_BEFORE_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_HOUR_BEFORE_NOTIFICATION, value).apply()
    
    var notificationSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_SOUND, value).apply()
    
    var notificationVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_VIBRATION, value).apply()
    
    /**
     * Schedule notifications for an event based on current preferences
     */
    fun scheduleEventNotification(event: Event) {
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications are disabled, not scheduling for event: ${event.title}")
            return
        }
        
        if (event.id == null || event.startTime == null) {
            Log.e(TAG, "Cannot schedule notification for event with null id or start time")
            return
        }
        
        // Use the Builder to schedule notifications
        val builder = NotificationWorker.Builder(context)
        builder.scheduleNotificationForEvent(event)
        
        Log.d(TAG, "Scheduled notifications for event: ${event.title}")
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
            apply()
        }
        Log.d(TAG, "Notification preferences reset to defaults")
    }
    
    /**
     * Request notification permissions if needed
     */
    fun requestNotificationPermissionIfNeeded() {
        // Permission handling is done through the activity
        // This is just a placeholder for future implementation
    }
} 