package com.petar.smrdici.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petar.smrdici.data.model.EventAssignee

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast for notification")
        
        val eventId = intent.getStringExtra("EVENT_ID")
        val title = intent.getStringExtra("EVENT_TITLE")
        val message = intent.getStringExtra("EVENT_MESSAGE")
        val assignee = intent.getStringExtra("EVENT_ASSIGNEE") ?: EventAssignee.EVERYONE.name
        
        if (eventId == null || title == null || message == null) {
            Log.e(TAG, "Missing required data in notification intent: eventId=$eventId, title=$title")
            return
        }
        
        Log.d(TAG, "Showing notification for event: $title, assignee: $assignee")
        
        val notificationService = NotificationService(context)
        notificationService.showEventNotification(eventId, title, message, assignee)
    }
} 