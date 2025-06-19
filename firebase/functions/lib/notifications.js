"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.processImmediateNotification = exports.processScheduledNotifications = void 0;
const functions = require("firebase-functions");
const admin = require("firebase-admin");
// Initialize Firestore
const db = admin.firestore();
/**
 * Cloud Function that runs periodically to check for notifications that need to be sent
 * This runs every minute to check for any notifications due for delivery
 */
exports.processScheduledNotifications = functions
    .region('europe-west1')
    .pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();
    try {
        // Query notifications that are:
        // 1. Scheduled (not cancelled)
        // 2. Due to be sent (scheduledFor <= now)
        const notificationsSnapshot = await db
            .collection('notifications')
            .where('status', '==', 'scheduled')
            .where('scheduledFor', '<=', now)
            .limit(100) // Process in batches to avoid timeout
            .get();
        if (notificationsSnapshot.empty) {
            console.log('No notifications to process');
            return null;
        }
        console.log(`Found ${notificationsSnapshot.size} notifications to process`);
        const promises = [];
        // Process each notification
        for (const doc of notificationsSnapshot.docs) {
            const notification = doc.data();
            const { recipients, title, message, eventId } = notification;
            if (!recipients || recipients.length === 0) {
                console.warn(`Notification ${doc.id} has no recipients`);
                // Mark as processed
                promises.push(doc.ref.update({
                    status: 'processed',
                    processedAt: admin.firestore.FieldValue.serverTimestamp(),
                    error: 'No recipients specified'
                }));
                continue;
            }
            // Retrieve FCM tokens for all recipients
            promises.push(sendNotificationToRecipients(recipients, title, message, eventId, doc));
        }
        await Promise.all(promises);
        console.log('Finished processing notifications');
        return null;
    }
    catch (error) {
        console.error('Error processing scheduled notifications:', error);
        return null;
    }
});
/**
 * Function to handle immediate notifications created in Firestore
 * Triggers when a new document is created in the notifications collection with status 'immediate'
 */
exports.processImmediateNotification = functions
    .region('europe-west1')
    .firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snapshot, context) => {
    const notification = snapshot.data();
    // Only process immediate notifications
    if (notification.status !== 'immediate') {
        return null;
    }
    const { recipients, title, message, eventId } = notification;
    if (!recipients || recipients.length === 0) {
        console.warn(`Notification ${snapshot.id} has no recipients`);
        // Mark as processed with error
        await snapshot.ref.update({
            status: 'processed',
            processedAt: admin.firestore.FieldValue.serverTimestamp(),
            error: 'No recipients specified'
        });
        return null;
    }
    try {
        // Send notification to all recipients
        await sendNotificationToRecipients(recipients, title, message, eventId, snapshot);
        return null;
    }
    catch (error) {
        console.error('Error processing immediate notification:', error);
        await snapshot.ref.update({
            error: `Failed to process: ${(error === null || error === void 0 ? void 0 : error.message) || 'Unknown error'}`
        });
        return null;
    }
});
/**
 * Helper function to send notification to all recipients
 */
async function sendNotificationToRecipients(recipients, title, message, eventId, docSnapshot) {
    try {
        // Get tokens for all recipients
        const tokenPromises = recipients.map(userId => db.collection('user_tokens').doc(userId).get());
        const tokenSnapshots = await Promise.all(tokenPromises);
        const tokens = [];
        // Extract valid tokens
        for (const tokenDoc of tokenSnapshots) {
            if (tokenDoc.exists) {
                const userData = tokenDoc.data();
                if (userData && userData.token) {
                    tokens.push(userData.token);
                }
            }
        }
        if (tokens.length === 0) {
            console.warn(`No valid FCM tokens found for notification ${docSnapshot.id}`);
            await docSnapshot.ref.update({
                status: 'processed',
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
                error: 'No valid FCM tokens found for recipients'
            });
            return;
        }
        // Prepare notification payload
        const payload = {
            notification: {
                title: title,
                body: message,
                clickAction: 'OPEN_APP'
            },
            data: {
                title: title,
                body: message
            }
        };
        // Add event ID if available
        if (eventId && payload.data) {
            payload.data.eventId = eventId;
        }
        // Send notification to all tokens
        const response = await admin.messaging().sendToDevice(tokens, payload);
        // Log results
        console.log(`Sent notification to ${tokens.length} devices`);
        console.log(`Successful: ${response.successCount}, Failed: ${response.failureCount}`);
        // Update notification status
        await docSnapshot.ref.update({
            status: 'processed',
            processedAt: admin.firestore.FieldValue.serverTimestamp(),
            successCount: response.successCount,
            failureCount: response.failureCount
        });
    }
    catch (error) {
        console.error(`Error sending notification ${docSnapshot.id}:`, error);
        await docSnapshot.ref.update({
            status: 'error',
            processedAt: admin.firestore.FieldValue.serverTimestamp(),
            error: (error === null || error === void 0 ? void 0 : error.message) || 'Unknown error'
        });
    }
}
//# sourceMappingURL=notifications.js.map