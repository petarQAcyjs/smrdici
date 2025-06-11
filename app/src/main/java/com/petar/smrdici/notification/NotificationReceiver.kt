package com.petar.smrdici.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationReceiver", "Received broadcast for notification")
        
        val eventId = intent.getStringExtra("EVENT_ID") ?: return
        val title = intent.getStringExtra("EVENT_TITLE") ?: return
        val message = intent.getStringExtra("EVENT_MESSAGE") ?: return
        
        val notificationService = NotificationService(context)
        notificationService.showEventNotification(eventId, title, message)
    }
} 