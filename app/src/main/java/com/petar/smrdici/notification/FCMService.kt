package com.petar.smrdici.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.petar.smrdici.R
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.ui.MainActivity
import java.util.Random

/**
 * Service to handle Firebase Cloud Messaging (FCM) notifications.
 * This handles notifications when the app is in the background or closed.
 */
class FCMService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMService"
        private const val FCM_NOTIFICATION_ID = 3000 // Different from local notification IDs
        private val random = Random()
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")
        
        // Check if the message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "FCM Notification Message: ${notification.title} / ${notification.body}")
            
            // Process the FCM notification
            showNotification(
                notification.title ?: "New event",
                notification.body ?: "You have a new event",
                remoteMessage.data
            )
        } ?: run {
            // If there's no notification payload, check data payload
            if (remoteMessage.data.isNotEmpty()) {
                Log.d(TAG, "FCM Data Message: ${remoteMessage.data}")
                
                // Extract notification information from data payload
                val title = remoteMessage.data["title"] ?: "New event"
                val message = remoteMessage.data["body"] ?: "You have a new event"
                
                // Show notification using the data payload
                showNotification(title, message, remoteMessage.data)
            }
        }
    }
    
    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token received: $token")
        
        // Send the new token to the server
        sendRegistrationToServer(token)
    }
    
    /**
     * Send FCM registration token to Firestore
     */
    private fun sendRegistrationToServer(token: String) {
        // Get the current user ID
        val userId = SmrdiciApplication.getInstance().getCurrentUserId()
        
        // If user is signed in, store the token in Firestore
        if (userId.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            val tokenData = hashMapOf(
                "token" to token,
                "updatedAt" to System.currentTimeMillis(),
                "platform" to "android"
            )
            
            db.collection("user_tokens")
                .document(userId)
                .set(tokenData)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token stored in Firestore for user $userId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to store FCM token in Firestore", e)
                }
        } else {
            Log.w(TAG, "User not signed in, FCM token not stored")
        }
    }
    
    /**
     * Show a notification from FCM payload
     */
    private fun showNotification(title: String, message: String, data: Map<String, String>) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = data["eventId"]?.hashCode() ?: (FCM_NOTIFICATION_ID + random.nextInt(1000))
        
        // Create intent to open the app and pass event ID if available
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            
            // Add event ID if available
            data["eventId"]?.let { eventId ->
                putExtra("EVENT_ID", eventId)
                action = "EVENT_NOTIFICATION"
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val builder = NotificationCompat.Builder(this, NotificationService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification_png)
        
        // Check user preferences for sound and vibration
        val appNotificationManager = SmrdiciApplication.getNotificationManager()
        
        if (appNotificationManager.notificationSoundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
        
        if (appNotificationManager.notificationVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }
        
        // Show the notification
        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "FCM notification shown: $title (ID: $notificationId)")
    }
} 