/**
 * MediTrack Cloud Functions
 *
 * Firestore triggers for order status notifications.
 * Sends FCM push notifications to patients when their order status changes.
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

// ═══════════════════════════════════════════════════════════════════════════
// Order Status Notification Trigger
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Triggered when an order document is updated in Firestore.
 * Sends a push notification to the patient if the status changed.
 */
export const onOrderStatusChange = functions.firestore
  .document("orders/{orderId}")
  .onUpdate(async (change, context) => {
    const orderId = context.params.orderId;
    const beforeData = change.before.data();
    const afterData = change.after.data();

    // Check if status actually changed
    const oldStatus = beforeData.status;
    const newStatus = afterData.status;

    if (oldStatus === newStatus) {
      console.log(`Order ${orderId}: Status unchanged (${oldStatus})`);
      return null;
    }

    console.log(`Order ${orderId}: Status changed from ${oldStatus} to ${newStatus}`);

    // Get patient's FCM token
    const patientId = afterData.patientId;
    if (!patientId) {
      console.error(`Order ${orderId}: No patientId found`);
      return null;
    }

    const patientDoc = await db.collection("users").doc(patientId).get();
    if (!patientDoc.exists) {
      console.error(`Patient ${patientId} not found`);
      return null;
    }

    const patientData = patientDoc.data();
    const fcmToken = patientData?.fcmToken;

    if (!fcmToken) {
      console.log(`Patient ${patientId} has no FCM token registered`);
      return null;
    }

    // Build notification content based on status
    const pharmacyName = afterData.pharmacyName || "Pharmacy";
    const notification = getNotificationContent(newStatus, pharmacyName);

    if (!notification) {
      console.log(`No notification configured for status: ${newStatus}`);
      return null;
    }

    // Send the push notification
    try {
      const message: admin.messaging.Message = {
        token: fcmToken,
        notification: {
          title: notification.title,
          body: notification.body,
        },
        data: {
          type: "order_status",
          orderId: orderId,
          status: newStatus,
          pharmacyName: pharmacyName,
          click_action: "OPEN_ORDER_TRACKING",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "meditrack_orders",
            priority: "high",
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
        apns: {
          payload: {
            aps: {
              sound: "default",
              badge: 1,
            },
          },
        },
      };

      const response = await messaging.send(message);
      console.log(`Notification sent successfully: ${response}`);

      // Log the notification in Firestore for audit
      await db.collection("notificationLogs").add({
        orderId: orderId,
        patientId: patientId,
        status: newStatus,
        title: notification.title,
        body: notification.body,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        fcmResponse: response,
      });

      return { success: true, messageId: response };
    } catch (error: unknown) {
      const err = error as { code?: string; message?: string };
      console.error(`Failed to send notification: ${err.message}`);

      // Handle invalid token (user uninstalled app or token expired)
      if (err.code === "messaging/invalid-registration-token" ||
        err.code === "messaging/registration-token-not-registered") {
        console.log(`Clearing invalid FCM token for patient ${patientId}`);
        await db.collection("users").doc(patientId).update({
          fcmToken: admin.firestore.FieldValue.delete(),
        });
      }

      return { success: false, error: err.message };
    }
  });

// ═══════════════════════════════════════════════════════════════════════════
// New Order Notification (for Pharmacy)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Triggered when a new order is created.
 * Sends a push notification to the pharmacy owner.
 */
export const onNewOrder = functions.firestore
  .document("orders/{orderId}")
  .onCreate(async (snapshot, context) => {
    const orderId = context.params.orderId;
    const orderData = snapshot.data();

    console.log(`New order created: ${orderId}`);

    const pharmacyId = orderData.pharmacyId;
    if (!pharmacyId) {
      console.error(`Order ${orderId}: No pharmacyId found`);
      return null;
    }

    // Get pharmacy owner's FCM token
    const pharmacyDoc = await db.collection("pharmacies").doc(pharmacyId).get();
    if (!pharmacyDoc.exists) {
      console.error(`Pharmacy ${pharmacyId} not found`);
      return null;
    }

    const pharmacyData = pharmacyDoc.data();
    const ownerId = pharmacyData?.ownerId;

    if (!ownerId) {
      console.error(`Pharmacy ${pharmacyId} has no owner`);
      return null;
    }

    const ownerDoc = await db.collection("users").doc(ownerId).get();
    if (!ownerDoc.exists) {
      console.error(`Owner ${ownerId} not found`);
      return null;
    }

    const ownerData = ownerDoc.data();
    const fcmToken = ownerData?.fcmToken;

    if (!fcmToken) {
      console.log(`Pharmacy owner ${ownerId} has no FCM token`);
      return null;
    }

    // Build notification
    const patientName = orderData.patientName || "A customer";
    const itemCount = orderData.items?.length || 1;
    const totalAmount = orderData.totalAmount || 0;

    try {
      const message: admin.messaging.Message = {
        token: fcmToken,
        notification: {
          title: "New Order Received!",
          body: `${patientName} ordered ${itemCount} item(s) - Rs.${totalAmount.toFixed(2)}`,
        },
        data: {
          type: "new_order",
          orderId: orderId,
          click_action: "OPEN_PHARMACY_ORDERS",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "meditrack_orders",
            priority: "high",
            defaultSound: true,
          },
        },
      };

      const response = await messaging.send(message);
      console.log(`New order notification sent: ${response}`);
      return { success: true, messageId: response };
    } catch (error: unknown) {
      const err = error as { message?: string };
      console.error(`Failed to send new order notification: ${err.message}`);
      return { success: false, error: err.message };
    }
  });

// ═══════════════════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════════════════

interface NotificationContent {
  title: string;
  body: string;
}

function getNotificationContent(
  status: string,
  pharmacyName: string
): NotificationContent | null {
  switch (status) {
    case "CONFIRMED":
      return {
        title: "Order Accepted",
        body: `Your order from ${pharmacyName} has been accepted and is being processed.`,
      };

    case "PREPARING":
      return {
        title: "Preparing Your Medicine",
        body: `${pharmacyName} is preparing your order. It will be ready soon.`,
      };

    case "READY":
      return {
        title: "Medicine Ready!",
        body: `Your medicine is ready for pickup at ${pharmacyName}.`,
      };

    case "SHIPPED":
      return {
        title: "Out for Delivery",
        body: `Your order from ${pharmacyName} is on the way!`,
      };

    case "DELIVERED":
      return {
        title: "Order Delivered",
        body: `Your order from ${pharmacyName} has been delivered. Stay healthy!`,
      };

    case "CANCELLED":
      return {
        title: "Order Cancelled",
        body: `Your order from ${pharmacyName} has been cancelled.`,
      };

    default:
      return null;
  }
}

// ═══════════════════════════════════════════════════════════════════════════
// Scheduled Cleanup (Optional)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Scheduled function to clean up old notification logs.
 * Runs daily at midnight.
 */
export const cleanupNotificationLogs = functions.pubsub
  .schedule("0 0 * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async () => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const oldLogs = await db
      .collection("notificationLogs")
      .where("sentAt", "<", thirtyDaysAgo)
      .limit(500)
      .get();

    if (oldLogs.empty) {
      console.log("No old notification logs to clean up");
      return null;
    }

    const batch = db.batch();
    oldLogs.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`Cleaned up ${oldLogs.size} old notification logs`);
    return null;
  });

// ═══════════════════════════════════════════════════════════════════════════
// Payment Verification (Server-side)
// ═══════════════════════════════════════════════════════════════════════════

// Export the payment verification function from the separate module
export { verifyRazorpayPayment } from "./verifyRazorpayPayment";
