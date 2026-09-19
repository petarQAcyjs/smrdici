package com.petar.smrdici.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.repository.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Receiver that's triggered when the device completes booting
 * Used to reschedule all event notifications after a device restart
 */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, restoring event notifications")
            
            // Use a coroutine to perform database operations
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    // Get the EventRepository
                    val repository = EventRepository(
                        FirebaseFirestore.getInstance(),
                        FirebaseAuth.getInstance(),
                        context
                    )
                    
                    // Get all events from the repository
                    val allEvents = repository.getAllEvents()
                    
                    // Find future events
                    val futureEvents = allEvents.filter { event ->
                        event.startTime?.toDate()?.after(Date()) == true
                    }
                    
                    Log.d(TAG, "Found ${futureEvents.size} future events to reschedule notifications for")
                    
                    // Reschedule notifications for each future event
                    futureEvents.forEach { event ->
                        if (event.id != null) {
                            val notificationHelper = NotificationHelper.getInstance(context)
                            // scheduleNotificationsForEvent leaves still-armed alarms alone on
                            // its own, so this only actually reschedules when they're genuinely
                            // gone (i.e. after a real reboot) or the event's time has changed
                            notificationHelper.scheduleNotificationsForEvent(event)
                            Log.d(TAG, "Checked notifications for event: ${event.title}")
                        }
                    }
                    
                    Log.d(TAG, "Completed rescheduling notifications after boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error rescheduling notifications after boot", e)
                }
            }
        }
    }
} 