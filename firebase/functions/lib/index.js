"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.processImmediateNotification = exports.processScheduledNotifications = void 0;
const admin = require("firebase-admin");
const notifications = require("./notifications");
// Initialize Firebase Admin SDK
admin.initializeApp();
// Export our Cloud Functions
exports.processScheduledNotifications = notifications.processScheduledNotifications;
exports.processImmediateNotification = notifications.processImmediateNotification;
//# sourceMappingURL=index.js.map